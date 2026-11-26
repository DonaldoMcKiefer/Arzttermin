package com.kiefer.arzttermin.repository;

import com.kiefer.arzttermin.domain.Practice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PracticeRepository extends JpaRepository<Practice, UUID> {
    Optional<Practice> findByUsername(String username);
}
