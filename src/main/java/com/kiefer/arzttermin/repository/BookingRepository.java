package com.kiefer.arzttermin.repository;

import com.kiefer.arzttermin.domain.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByAppointmentOffer_Id(UUID offerId);

    Optional<Booking> findByAppointmentSlot_Id(UUID slotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"appointmentSlot", "appointmentSlot.practice", "searchRequest"})
    List<Booking> findBySearchRequest_Patient_UsernameOrderByBookedAtDesc(String username);

    @EntityGraph(attributePaths = {"appointmentSlot", "searchRequest", "searchRequest.patient"})
    List<Booking> findByAppointmentSlot_Practice_UsernameOrderByBookedAtDesc(String username);

    @EntityGraph(attributePaths = {"appointmentSlot", "appointmentSlot.practice", "searchRequest"})
    Optional<Booking> findByIdAndSearchRequest_Patient_Username(UUID id, String username);

    @EntityGraph(attributePaths = {"appointmentSlot", "searchRequest", "searchRequest.patient"})
    Optional<Booking> findByIdAndAppointmentSlot_Practice_Username(UUID id, String username);
}
