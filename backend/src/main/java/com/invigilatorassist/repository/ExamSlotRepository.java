package com.invigilatorassist.repository;

import com.invigilatorassist.domain.ExamSlot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ExamSlotRepository extends JpaRepository<ExamSlot, Long> {

    @EntityGraph(attributePaths = {"subject", "subject.department", "subject.instructor"})
    List<ExamSlot> findAllByInstitutionIdAndExamDateAndStartTime(Long institutionId, LocalDate date, LocalTime start);

    @EntityGraph(attributePaths = {"subject"})
    List<ExamSlot> findAllByInstitutionIdOrderByExamDateAscStartTimeAsc(Long institutionId);
}
