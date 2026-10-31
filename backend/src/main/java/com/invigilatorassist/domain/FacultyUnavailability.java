package com.invigilatorassist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "faculty_unavailability")
public class FacultyUnavailability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(name = "unavailable_on", nullable = false)
    private LocalDate unavailableOn;

    private String reason;

    protected FacultyUnavailability() {}

    public FacultyUnavailability(Faculty faculty, LocalDate unavailableOn, String reason) {
        this.faculty = faculty;
        this.unavailableOn = unavailableOn;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Faculty getFaculty() { return faculty; }
    public LocalDate getUnavailableOn() { return unavailableOn; }
    public String getReason() { return reason; }
}
