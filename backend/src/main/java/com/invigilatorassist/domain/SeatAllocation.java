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

@Entity
@Table(name = "seat_allocation")
public class SeatAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_allocation_id", nullable = false)
    private RoomAllocation roomAllocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_slot_id", nullable = false)
    private ExamSlot examSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    protected SeatAllocation() {}

    SeatAllocation(RoomAllocation roomAllocation, ExamSlot examSlot, Student student, int seatNo) {
        this.roomAllocation = roomAllocation;
        this.examSlot = examSlot;
        this.student = student;
        this.seatNo = seatNo;
    }

    public Long getId() { return id; }
    public RoomAllocation getRoomAllocation() { return roomAllocation; }
    public ExamSlot getExamSlot() { return examSlot; }
    public Student getStudent() { return student; }
    public int getSeatNo() { return seatNo; }
}
