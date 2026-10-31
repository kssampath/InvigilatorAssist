package com.invigilatorassist.repository;

import com.invigilatorassist.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByInstitutionIdAndUsername(Long institutionId, String username);
}
