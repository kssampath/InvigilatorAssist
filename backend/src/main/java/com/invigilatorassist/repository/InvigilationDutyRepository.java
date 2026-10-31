package com.invigilatorassist.repository;

import com.invigilatorassist.domain.InvigilationDuty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public interface InvigilationDutyRepository extends JpaRepository<InvigilationDuty, Long> {

    /** Faculty who already have a duty overlapping [start, end) on that date. */
    @Query("""
            select d.faculty.id from InvigilationDuty d
            where d.roomAllocation.institutionId = :institutionId and d.roomAllocation.examDate = :date
              and d.roomAllocation.startTime < :end and d.roomAllocation.endTime > :start""")
    Set<Long> findBusyFacultyIds(@Param("institutionId") Long institutionId, @Param("date") LocalDate date,
                                 @Param("start") LocalTime start, @Param("end") LocalTime end);

    /** Does this faculty member have a duty overlapping the window, other than the excluded one? */
    @Query("""
            select count(d) > 0 from InvigilationDuty d
            where d.faculty.id = :facultyId and d.id <> :excludeDutyId
              and d.roomAllocation.examDate = :date
              and d.roomAllocation.startTime < :end and d.roomAllocation.endTime > :start""")
    boolean hasOverlappingDuty(@Param("facultyId") Long facultyId, @Param("excludeDutyId") Long excludeDutyId,
                               @Param("date") LocalDate date, @Param("start") LocalTime start,
                               @Param("end") LocalTime end);

    @Query("""
            select d from InvigilationDuty d
            join fetch d.roomAllocation ra join fetch ra.room
            where d.faculty.id = :facultyId and ra.examDate >= :from
            order by ra.examDate, ra.startTime""")
    List<InvigilationDuty> findUpcomingForFaculty(@Param("facultyId") Long facultyId, @Param("from") LocalDate from);

    @Query("""
            select d from InvigilationDuty d
            join fetch d.roomAllocation ra join fetch ra.room join fetch d.faculty f join fetch f.user
            where ra.institutionId = :institutionId and ra.examDate >= :from
            order by ra.examDate, ra.startTime, ra.room.code""")
    List<InvigilationDuty> findUpcomingForInstitution(@Param("institutionId") Long institutionId,
                                                      @Param("from") LocalDate from);

    @Query("""
            select d from InvigilationDuty d
            join fetch d.roomAllocation ra join fetch ra.room join fetch d.faculty f join fetch f.user
            where ra.id in :roomAllocationIds""")
    List<InvigilationDuty> findAllForRoomAllocations(@Param("roomAllocationIds") List<Long> roomAllocationIds);
}
