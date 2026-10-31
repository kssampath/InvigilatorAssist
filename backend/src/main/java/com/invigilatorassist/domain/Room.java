package com.invigilatorassist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Room {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(nullable = false)
    private String code;

    private String building;
    private Integer floor;

    @Column(nullable = false)
    private int capacity;

    /** Ground floor, ramp or lift access. */
    @Column(nullable = false)
    private boolean accessible;

    @Column(nullable = false)
    private boolean active = true;

    protected Room() {}

    public Room(Long institutionId, String code, String building, Integer floor, int capacity, boolean accessible) {
        this.institutionId = institutionId;
        this.code = code;
        update(building, floor, capacity, accessible);
    }

    public void update(String building, Integer floor, int capacity, boolean accessible) {
        this.building = building;
        this.floor = floor;
        this.capacity = capacity;
        this.accessible = accessible;
    }

    public Long getId() { return id; }
    public Long getInstitutionId() { return institutionId; }
    public String getCode() { return code; }
    public String getBuilding() { return building; }
    public Integer getFloor() { return floor; }
    public int getCapacity() { return capacity; }
    public boolean isAccessible() { return accessible; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
