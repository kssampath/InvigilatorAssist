package com.invigilatorassist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Subject {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int semester;

    /** Null = common subject for every department in that semester. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** Never assigned to invigilate this subject's exam. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Faculty instructor;

    /** True for electives: only students enrolled through the enrollment list write it. */
    @Column(nullable = false)
    private boolean elective;

    protected Subject() {}

    public Subject(Long institutionId, String code, String name, int semester, Department department,
                   Faculty instructor, boolean elective) {
        this.institutionId = institutionId;
        this.code = code;
        update(name, semester, department, instructor, elective);
    }

    public void update(String name, int semester, Department department, Faculty instructor, boolean elective) {
        this.name = name;
        this.semester = semester;
        this.department = department;
        this.instructor = instructor;
        this.elective = elective;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getSemester() { return semester; }
    public Department getDepartment() { return department; }
    public Faculty getInstructor() { return instructor; }
    public boolean isElective() { return elective; }
}
