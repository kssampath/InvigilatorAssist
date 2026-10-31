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

import java.time.OffsetDateTime;

/** Faculty A offers their duty in exchange for faculty B's duty. Accepting swaps the two invigilators. */
@Entity
@Table(name = "swap_request")
public class SwapRequest {
    public enum Status { PENDING, ACCEPTED, REJECTED, CANCELLED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_duty_id", nullable = false)
    private InvigilationDuty requesterDuty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_duty_id", nullable = false)
    private InvigilationDuty targetDuty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    private String note;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    protected SwapRequest() {}

    public SwapRequest(Long institutionId, InvigilationDuty requesterDuty, InvigilationDuty targetDuty, String note) {
        this.institutionId = institutionId;
        this.requesterDuty = requesterDuty;
        this.targetDuty = targetDuty;
        this.note = note;
    }

    public void decide(Status newStatus) {
        this.status = newStatus;
        this.decidedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public InvigilationDuty getRequesterDuty() { return requesterDuty; }
    public InvigilationDuty getTargetDuty() { return targetDuty; }
    public Status getStatus() { return status; }
    public String getNote() { return note; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}
