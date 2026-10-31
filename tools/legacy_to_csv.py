#!/usr/bin/env python3
"""Convert the legacy PHP app's MySQL dump (legacy/ems.sql) into the new platform's import CSVs.

Usage:
    python3 tools/legacy_to_csv.py legacy/ems.sql out/ --email-domain college.edu

Upload the files in this order through the admin import screen (or POST /api/admin/import/{kind}):
    departments.csv, rooms.csv, faculty.csv, subjects.csv, students.csv, backlogs.csv

What it maps:
    student   -> students.csv  (department = letters of the section, e.g. "CSE5A" -> CSE)
    student.DE (chosen elective) and back (re-take papers) -> backlogs.csv
    subject   -> subjects.csv  (subtype "DE" -> elective of the students' semester)
    classroom -> rooms.csv
    teacher   -> faculty.csv   (old "designation" number was the duty quota -> max_duties).
                 The old table has no emails, so placeholder addresses are generated; fix them before go-live.
    Old plaintext passwords are NOT migrated: everyone gets a fresh temporary password on import.
"""
import argparse
import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

INSERT_RE = re.compile(r"INSERT INTO `(\w+)` \(([^)]*)\) VALUES\s*(.*?);\s*$", re.S | re.M)


def parse_values(blob):
    """Parses "(1, 'a', NULL), (2, 'b''c', 3)" into lists of Python values."""
    rows, row, i, n = [], None, 0, len(blob)
    while i < n:
        ch = blob[i]
        if ch == "(" and row is None:
            row, i = [], i + 1
            continue
        if row is None or ch in " \n\r\t,":
            i += 1
            continue
        if ch == ")":
            rows.append(row)
            row, i = None, i + 1
            continue
        if ch == "'":
            j, buf = i + 1, []
            while j < n:
                if blob[j] == "\\" and j + 1 < n:
                    buf.append(blob[j + 1]); j += 2; continue
                if blob[j] == "'" and j + 1 < n and blob[j + 1] == "'":
                    buf.append("'"); j += 2; continue
                if blob[j] == "'":
                    break
                buf.append(blob[j]); j += 1
            row.append("".join(buf))
            i = j + 1
            continue
        j = i
        while j < n and blob[j] not in ",)":
            j += 1
        token = blob[i:j].strip()
        row.append(None if token.upper() == "NULL" else token)
        i = j
    return rows


def load_tables(sql_text):
    tables = defaultdict(list)
    for m in INSERT_RE.finditer(sql_text):
        cols = [c.strip(" `") for c in m.group(2).split(",")]
        for values in parse_values(m.group(3)):
            tables[m.group(1)].append(dict(zip(cols, values)))
    return tables


def code(value):
    return re.sub(r"\s+", "", value or "").upper()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dump")
    ap.add_argument("out_dir")
    ap.add_argument("--email-domain", default="example.edu", help="domain for placeholder faculty emails")
    ap.add_argument("--default-quota", type=int, default=4, help="max_duties when the old row has none")
    args = ap.parse_args()

    tables = load_tables(Path(args.dump).read_text(encoding="latin-1"))
    out = Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)

    students = tables.get("student", [])
    subjects = tables.get("subject", [])

    # Departments come from the section prefix, e.g. "CSE5A" / "CSE 5C" -> CSE.
    def dept_of(section):
        m = re.match(r"\s*([A-Za-z]+)", section or "")
        return m.group(1).upper() if m else None

    depts = sorted({d for d in (dept_of(s.get("section")) for s in students) if d})

    # Electives ("DE") belong to the semester of the students who chose them.
    elective_sem = {}
    for s in students:
        if s.get("DE"):
            elective_sem.setdefault(code(s["DE"]), s.get("semister"))

    def write(name, header, rows):
        with open(out / name, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(header)
            w.writerows(rows)
        print(f"{name}: {len(rows)} rows")

    write("departments.csv", ["code", "name"], [[d, d] for d in depts])

    write("rooms.csv", ["code", "building", "floor", "capacity", "accessible"],
          [[code(r["roomno"]), "", "", r["seatingcapacity"], "false"] for r in tables.get("classroom", [])])

    write("faculty.csv", ["employee_no", "full_name", "email", "department_code", "designation", "max_duties"],
          [[f"F{t['fno']}", t["fname"].strip(), f"f{t['fno']}@{args.email_domain}", "", "Faculty",
            t.get("designation") or args.default_quota] for t in tables.get("teacher", [])])

    subject_rows = []
    for s in subjects:
        is_elective = (s.get("subtype") or "").upper() == "DE"
        sem = elective_sem.get(code(s["subjectcode"])) if is_elective else s.get("subtype")
        if not sem:
            # Old course codes carry the semester as their second digit: CS1551 -> 5, MA1307 -> 3.
            digits = re.sub(r"\D", "", s["subjectcode"])
            sem = digits[1] if len(digits) >= 2 and digits[1] != "0" else None
        if not sem or not str(sem).isdigit():
            print(f"  skipped subject {s['subjectcode']}: no semester", file=sys.stderr)
            continue
        subject_rows.append([code(s["subjectcode"]), s["subname"].strip(), sem, "", "",
                             "true" if is_elective else "false"])
    write("subjects.csv", ["code", "name", "semester", "department_code", "instructor_employee_no", "elective"],
          subject_rows)

    write("students.csv", ["reg_no", "name", "section", "semester", "department_code", "needs_accessible_room"],
          [[s["regno"], s["name"].strip(), (s.get("section") or "").replace(" ", ""), s["semister"],
            dept_of(s.get("section")) or "", "false"] for s in students])

    known_subjects = {r[0] for r in subject_rows}
    known_students = {str(s["regno"]) for s in students}
    enrollments = set()
    for b in tables.get("back", []):
        enrollments.add((str(b["regno"]), code(b["subjectcode"])))
    for s in students:
        if s.get("DE"):
            enrollments.add((str(s["regno"]), code(s["DE"])))
    valid = sorted(e for e in enrollments if e[0] in known_students and e[1] in known_subjects)
    for e in sorted(enrollments - set(valid)):
        print(f"  skipped enrollment {e}: unknown student or subject", file=sys.stderr)
    write("backlogs.csv", ["reg_no", "subject_code"], [list(e) for e in valid])


if __name__ == "__main__":
    main()
