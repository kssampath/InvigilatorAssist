package com.invigilatorassist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "invigilation_duty")
public class InvigilationDuty {
    public enum DutyRole { INVIGILATOR, RELIEVER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_allocation_id", nullable = false)
    private RoomAllocation roomAllocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Enumerated(EnumType.STRING)
    @Column(name = "duty_role", nullable = false)
    private DutyRole dutyRole = DutyRole.INVIGILATOR;

    protected InvigilationDuty() {}

    InvigilationDuty(RoomAllocation roomAllocation, Faculty faculty, DutyRole dutyRole) {
        this.roomAllocation = roomAllocation;
        this.faculty = faculty;
        this.dutyRole = dutyRole;
    }

    /** Used by an accepted swap: the duty moves to another faculty member. */
    public void reassignTo(Faculty other) {
        this.faculty = other;
    }

    public Long getId() { return id; }
    public RoomAllocation getRoomAllocation() { return roomAllocation; }
    public Faculty getFaculty() { return faculty; }
    public DutyRole getDutyRole() { return dutyRole; }
}
