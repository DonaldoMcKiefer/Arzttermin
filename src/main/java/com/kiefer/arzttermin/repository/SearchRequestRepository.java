package com.kiefer.arzttermin.repository;

import com.kiefer.arzttermin.domain.SearchRequest;
import com.kiefer.arzttermin.domain.SearchRequestStatus;
import com.kiefer.arzttermin.domain.ServiceTier;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SearchRequestRepository extends JpaRepository<SearchRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from SearchRequest request where request.id = :id")
    Optional<SearchRequest> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"patient", "availabilityWindows"})
    List<SearchRequest> findByStatusOrderByCreatedAtAsc(SearchRequestStatus status);

    @EntityGraph(attributePaths = {"patient", "availabilityWindows"})
    List<SearchRequest> findByPatient_UsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"patient", "availabilityWindows"})
    Optional<SearchRequest> findByIdAndPatient_Username(UUID id, String username);

    @EntityGraph(attributePaths = {"patient", "availabilityWindows"})
    List<SearchRequest> findByServiceTierOrderByCreatedAtAsc(ServiceTier serviceTier);
}
