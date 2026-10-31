package com.invigilatorassist.repository;

import com.invigilatorassist.domain.Subject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByInstitutionIdAndCode(Long institutionId, String code);

    @EntityGraph(attributePaths = {"department", "instructor"})
    List<Subject> findAllByInstitutionIdOrderByCode(Long institutionId);
}
