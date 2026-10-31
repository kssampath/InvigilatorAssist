package com.invigilatorassist.repository;

import com.invigilatorassist.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByInstitutionIdAndRegNo(Long institutionId, String regNo);

    Optional<Student> findByUserId(Long userId);

    /** Regular students for a common subject (no department). */
    List<Student> findAllByInstitutionIdAndSemester(Long institutionId, int semester);

    /** Regular students for a department subject. */
    List<Student> findAllByInstitutionIdAndSemesterAndDepartmentId(Long institutionId, int semester, Long departmentId);

    /** Students re-taking the subject from an earlier semester. */
    @Query("select s from Student s join s.backlogSubjects b where b.id = :subjectId")
    List<Student> findBacklogStudents(@Param("subjectId") Long subjectId);

    long countByInstitutionId(Long institutionId);
}
