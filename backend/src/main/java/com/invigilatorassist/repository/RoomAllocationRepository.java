package com.invigilatorassist.repository;

import com.invigilatorassist.domain.RoomAllocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public interface RoomAllocationRepository extends JpaRepository<RoomAllocation, Long> {

    boolean existsByInstitutionIdAndExamDateAndStartTime(Long institutionId, LocalDate date, LocalTime start);

    @EntityGraph(attributePaths = {"room"})
    List<RoomAllocation> findAllByInstitutionIdAndExamDateAndStartTimeOrderById(
            Long institutionId, LocalDate date, LocalTime start);

    /** Rooms already used by another sitting whose time overlaps [start, end) on that date. */
    @Query("""
            select ra.room.id from RoomAllocation ra
            where ra.institutionId = :institutionId and ra.examDate = :date
              and ra.startTime < :end and ra.endTime > :start""")
    Set<Long> findBusyRoomIds(@Param("institutionId") Long institutionId, @Param("date") LocalDate date,
                              @Param("start") LocalTime start, @Param("end") LocalTime end);
}
