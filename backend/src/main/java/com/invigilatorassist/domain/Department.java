package com.invigilatorassist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    protected Department() {}

    public Department(Long institutionId, String code, String name) {
        this.institutionId = institutionId;
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
