package com.invigilatorassist.repository;

import com.invigilatorassist.domain.SeatAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatAllocationRepository extends JpaRepository<SeatAllocation, Long> {

    @Query("""
            select sa from SeatAllocation sa
            join fetch sa.roomAllocation ra join fetch ra.room
            join fetch sa.examSlot es join fetch es.subject
            where sa.student.id = :studentId
            order by ra.examDate, ra.startTime""")
    List<SeatAllocation> findTimetableForStudent(@Param("studentId") Long studentId);

    @Query("""
            select sa from SeatAllocation sa
            join fetch sa.student join fetch sa.examSlot es join fetch es.subject
            where sa.roomAllocation.id in :roomAllocationIds
            order by sa.roomAllocation.id, sa.seatNo""")
    List<SeatAllocation> findAllForRoomAllocations(@Param("roomAllocationIds") List<Long> roomAllocationIds);

    /** Subject codes being written in a room: used to keep instructors away from their own subject. */
    @Query("""
            select distinct s.code from SeatAllocation sa join sa.examSlot es join es.subject s
            where sa.roomAllocation.id = :roomAllocationId""")
    List<String> findSubjectCodesInRoom(@Param("roomAllocationId") Long roomAllocationId);

    /** True when the faculty member teaches any subject being written in that room. */
    @Query("""
            select count(sa) > 0 from SeatAllocation sa
            where sa.roomAllocation.id = :roomAllocationId and sa.examSlot.subject.instructor.id = :facultyId""")
    boolean facultyTeachesSubjectInRoom(@Param("roomAllocationId") Long roomAllocationId,
                                        @Param("facultyId") Long facultyId);
}
