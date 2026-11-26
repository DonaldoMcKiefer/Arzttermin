package com.kiefer.arzttermin.repository;

import com.kiefer.arzttermin.domain.PortalFee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalFeeRepository extends JpaRepository<PortalFee, UUID> {

    Optional<PortalFee> findByBooking_Id(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select fee from PortalFee fee where fee.booking.id = :bookingId")
    Optional<PortalFee> findByBookingIdForUpdate(@Param("bookingId") UUID bookingId);

    @EntityGraph(attributePaths = {"booking", "booking.appointmentSlot", "booking.appointmentSlot.practice"})
    List<PortalFee> findByBooking_SearchRequest_Patient_UsernameOrderByCreatedAtDesc(String username);

    @EntityGraph(attributePaths = {"booking", "booking.appointmentSlot", "booking.appointmentSlot.practice"})
    Optional<PortalFee> findByIdAndBooking_SearchRequest_Patient_Username(UUID id, String username);
}
