package com.invigilatorassist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.time.LocalDateTime;

@Entity
public class Faculty {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "employee_no", nullable = false)
    private String employeeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private String designation;

    /** Quota of duties for the current exam season. */
    @Column(name = "max_duties", nullable = false)
    private int maxDuties;

    @Column(name = "duty_count", nullable = false)
    private int dutyCount;

    @Column(name = "last_duty_at")
    private LocalDateTime lastDutyAt;

    protected Faculty() {}

    public Faculty(Long institutionId, AppUser user, String employeeNo, Department department,
                   String designation, int maxDuties) {
        this.institutionId = institutionId;
        this.user = user;
        this.employeeNo = employeeNo;
        this.department = department;
        this.designation = designation;
        this.maxDuties = maxDuties;
    }

    public void recordDuty(LocalDateTime at) {
        dutyCount++;
        if (lastDutyAt == null || at.isAfter(lastDutyAt)) lastDutyAt = at;
    }

    /** Gives one duty of quota back when an allocation is cleared. lastDutyAt is left as is. */
    public void undoDuty() {
        if (dutyCount > 0) dutyCount--;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public AppUser getUser() { return user; }
    public String getEmployeeNo() { return employeeNo; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public int getMaxDuties() { return maxDuties; }
    public void setMaxDuties(int maxDuties) { this.maxDuties = maxDuties; }
    public int getDutyCount() { return dutyCount; }
    public LocalDateTime getLastDutyAt() { return lastDutyAt; }
}
