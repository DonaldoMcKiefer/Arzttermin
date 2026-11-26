package com.kiefer.arzttermin.repository;

import com.kiefer.arzttermin.domain.AppointmentOffer;
import com.kiefer.arzttermin.domain.OfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentOfferRepository extends JpaRepository<AppointmentOffer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from AppointmentOffer offer where offer.id = :id")
    Optional<AppointmentOffer> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"appointmentSlot", "appointmentSlot.practice", "searchRequest"})
    List<AppointmentOffer> findBySearchRequest_Patient_UsernameOrderByOfferedAtDesc(String username);

    @EntityGraph(attributePaths = {"appointmentSlot", "searchRequest", "searchRequest.patient"})
    List<AppointmentOffer> findByAppointmentSlot_Practice_UsernameOrderByOfferedAtDesc(String username);

    @EntityGraph(attributePaths = {"appointmentSlot", "appointmentSlot.practice", "searchRequest"})
    Optional<AppointmentOffer> findByIdAndSearchRequest_Patient_Username(UUID id, String username);

    @EntityGraph(attributePaths = {"appointmentSlot", "searchRequest", "searchRequest.patient"})
    Optional<AppointmentOffer> findByIdAndAppointmentSlot_Practice_Username(UUID id, String username);

    Optional<AppointmentOffer> findByAppointmentSlot_IdAndStatus(UUID slotId, OfferStatus status);

    List<AppointmentOffer> findByStatusAndExpiresAtLessThanEqual(OfferStatus status, Instant expiresAt);
}
