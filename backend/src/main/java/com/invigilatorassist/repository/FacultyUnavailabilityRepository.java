package com.invigilatorassist.repository;

import com.invigilatorassist.domain.FacultyUnavailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface FacultyUnavailabilityRepository extends JpaRepository<FacultyUnavailability, Long> {

    @Query("""
            select u.faculty.id from FacultyUnavailability u
            where u.faculty.institutionId = :institutionId and u.unavailableOn = :date""")
    Set<Long> findFacultyIdsUnavailableOn(@Param("institutionId") Long institutionId, @Param("date") LocalDate date);

    boolean existsByFacultyIdAndUnavailableOn(Long facultyId, LocalDate date);

    List<FacultyUnavailability> findAllByFacultyIdAndUnavailableOnGreaterThanEqualOrderByUnavailableOn(
            Long facultyId, LocalDate from);
}
