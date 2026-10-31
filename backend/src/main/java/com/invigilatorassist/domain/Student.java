package com.invigilatorassist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.util.HashSet;
import java.util.Set;

@Entity
public class Student {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private AppUser user;

    @Column(name = "reg_no", nullable = false)
    private String regNo;

    @Column(nullable = false)
    private String name;

    private String section;

    @Column(nullable = false)
    private int semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "needs_accessible_room", nullable = false)
    private boolean needsAccessibleRoom;

    /** Subjects from earlier semesters the student must re-take ("back" papers in the old system). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "backlog_enrollment",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "subject_id"))
    private Set<Subject> backlogSubjects = new HashSet<>();

    protected Student() {}

    public Student(Long institutionId, AppUser user, String regNo, String name, String section, int semester,
                   Department department, boolean needsAccessibleRoom) {
        this.institutionId = institutionId;
        this.user = user;
        this.regNo = regNo;
        update(name, section, semester, department, needsAccessibleRoom);
    }

    public void update(String name, String section, int semester, Department department, boolean needsAccessibleRoom) {
        this.name = name;
        this.section = section;
        this.semester = semester;
        this.department = department;
        this.needsAccessibleRoom = needsAccessibleRoom;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public AppUser getUser() { return user; }
    public String getRegNo() { return regNo; }
    public String getName() { return name; }
    public String getSection() { return section; }
    public int getSemester() { return semester; }
    public Department getDepartment() { return department; }
    public boolean isNeedsAccessibleRoom() { return needsAccessibleRoom; }
    public Set<Subject> getBacklogSubjects() { return backlogSubjects; }
}
