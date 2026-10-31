package com.invigilatorassist.repository;

import com.invigilatorassist.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByInstitutionIdAndCode(Long institutionId, String code);

    List<Department> findAllByInstitutionIdOrderByCode(Long institutionId);
}
