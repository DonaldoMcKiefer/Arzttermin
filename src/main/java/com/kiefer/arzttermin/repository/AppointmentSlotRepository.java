package com.kiefer.arzttermin.repository;

import com.kiefer.arzttermin.domain.AppointmentSlot;
import com.kiefer.arzttermin.domain.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select slot from AppointmentSlot slot where slot.id = :id")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"practice", "acceptedBillingTypes"})
    List<AppointmentSlot> findByPractice_UsernameOrderByStartAtDesc(String username);

    @EntityGraph(attributePaths = {"practice", "acceptedBillingTypes"})
    Optional<AppointmentSlot> findByIdAndPractice_Username(UUID id, String username);

    @EntityGraph(attributePaths = {"practice", "acceptedBillingTypes"})
    List<AppointmentSlot> findByStatusOrderByStartAtAsc(SlotStatus status);
}
