package com.kiefer.arzttermin.service;

import com.kiefer.arzttermin.domain.*;
import com.kiefer.arzttermin.repository.AppointmentOfferRepository;
import com.kiefer.arzttermin.repository.AppointmentSlotRepository;
import com.kiefer.arzttermin.repository.SearchRequestRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class OfferExpiryService {
    private final Clock clock;
    private final AppointmentOfferRepository offers;
    private final AppointmentSlotRepository slots;
    private final SearchRequestRepository requests;

    public OfferExpiryService(Clock clock, AppointmentOfferRepository offers,
                              AppointmentSlotRepository slots, SearchRequestRepository requests) {
        this.clock = clock;
        this.offers = offers;
        this.slots = slots;
        this.requests = requests;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    @Transactional
    public void expireDueOffers() {
        Instant now = Instant.now(clock);
        for (AppointmentOffer candidate : offers.findByStatusAndExpiresAtLessThanEqual(OfferStatus.PENDING, now)) {
            AppointmentOffer offer = offers.findByIdForUpdate(candidate.getId()).orElse(null);
            if (offer == null || offer.getStatus() != OfferStatus.PENDING || !offer.isExpiredAt(now)) continue;
            AppointmentSlot slot = slots.findByIdForUpdate(offer.getAppointmentSlot().getId()).orElse(null);
            SearchRequest request = requests.findByIdForUpdate(offer.getSearchRequest().getId()).orElse(null);
            if (slot == null || request == null) continue;
            offer.expire(now);
            if (slot.getStatus() == SlotStatus.OFFERED) slot.releaseAfterUnsuccessfulOffer();
            if (request.getStatus() == SearchRequestStatus.OFFERED) request.reopenAfterUnsuccessfulOffer();
        }
    }
}
