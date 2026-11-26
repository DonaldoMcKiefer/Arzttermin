package com.kiefer.arzttermin.repository;

import com.kiefer.arzttermin.domain.PracticeContact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PracticeContactRepository extends JpaRepository<PracticeContact, UUID> {

    @EntityGraph(attributePaths = {"practice"})
    List<PracticeContact> findBySearchRequest_IdOrderByContactedAtDesc(UUID searchRequestId);
}
