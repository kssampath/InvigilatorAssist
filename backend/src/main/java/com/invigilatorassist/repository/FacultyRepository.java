package com.invigilatorassist.repository;

import com.invigilatorassist.domain.Faculty;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Faculty> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "department"})
    Optional<Faculty> findByInstitutionIdAndEmployeeNo(Long institutionId, String employeeNo);

    @EntityGraph(attributePaths = {"user", "department"})
    List<Faculty> findAllByInstitutionIdOrderByEmployeeNo(Long institutionId);

    /**
     * Locks every faculty row of the institution for the rest of the transaction, so two admins allocating
     * different sittings at the same moment cannot both spend the same person's quota.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Faculty f where f.institutionId = :institutionId order by f.id")
    List<Faculty> lockAllForInstitution(@Param("institutionId") Long institutionId);
}
