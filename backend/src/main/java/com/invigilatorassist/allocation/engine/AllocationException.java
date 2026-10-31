package com.invigilatorassist.allocation.engine;

/** Raised when a sitting cannot be allocated (not enough rooms, seats or free invigilators). */
public class AllocationException extends RuntimeException {
    public AllocationException(String message) {
        super(message);
    }
}
