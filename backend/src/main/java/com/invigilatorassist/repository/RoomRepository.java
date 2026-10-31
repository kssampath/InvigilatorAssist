package com.invigilatorassist.repository;

import com.invigilatorassist.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {
    Optional<Room> findByInstitutionIdAndCode(Long institutionId, String code);

    List<Room> findAllByInstitutionIdOrderByCode(Long institutionId);

    List<Room> findAllByInstitutionIdAndActiveTrue(Long institutionId);
}
