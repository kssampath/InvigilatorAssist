package com.invigilatorassist.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** One room used in one sitting (all exams starting at the same date and time). */
@Entity
@Table(name = "room_allocation")
public class RoomAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @OneToMany(mappedBy = "roomAllocation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seatNo")
    private List<SeatAllocation> seats = new ArrayList<>();

    @OneToMany(mappedBy = "roomAllocation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvigilationDuty> duties = new ArrayList<>();

    protected RoomAllocation() {}

    public RoomAllocation(Long institutionId, LocalDate examDate, LocalTime startTime, LocalTime endTime, Room room) {
        this.institutionId = institutionId;
        this.examDate = examDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
    }

    public void addSeat(ExamSlot slot, Student student, int seatNo) {
        seats.add(new SeatAllocation(this, slot, student, seatNo));
    }

    public void addDuty(Faculty faculty, InvigilationDuty.DutyRole role) {
        duties.add(new InvigilationDuty(this, faculty, role));
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public LocalDate getExamDate() { return examDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public Room getRoom() { return room; }
    public List<SeatAllocation> getSeats() { return seats; }
    public List<InvigilationDuty> getDuties() { return duties; }
}
