CREATE TABLE patient_profile (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    city VARCHAR(120) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

CREATE TABLE practice (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    street VARCHAR(180) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    city VARCHAR(120) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL
);

CREATE TABLE search_request (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    specialty VARCHAR(50) NOT NULL,
    appointment_type VARCHAR(60) NOT NULL,
    location_text VARCHAR(160) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_km INTEGER NOT NULL,
    short_notice BOOLEAN NOT NULL,
    new_patient BOOLEAN NOT NULL,
    billing_type VARCHAR(30) NOT NULL,
    service_tier VARCHAR(30) NOT NULL,
    needed_by DATE,
    status VARCHAR(30) NOT NULL,
    personal_search_status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_search_request_patient FOREIGN KEY (patient_id) REFERENCES patient_profile (id),
    CONSTRAINT ck_search_request_radius CHECK (radius_km BETWEEN 1 AND 250),
    CONSTRAINT ck_search_request_billing CHECK (billing_type IN ('STATUTORY', 'PRIVATE', 'SELF_PAY')),
    CONSTRAINT ck_search_request_tier CHECK (service_tier IN ('AUTOMATIC_5', 'PERSONAL_25')),
    CONSTRAINT ck_search_request_status CHECK (status IN ('OPEN', 'OFFERED', 'BOOKED', 'CLOSED'))
);

CREATE INDEX ix_search_request_patient ON search_request (patient_id, created_at DESC);
CREATE INDEX ix_search_request_open_order ON search_request (created_at ASC) WHERE status = 'OPEN';
CREATE INDEX ix_search_request_personal_work ON search_request (personal_search_status, created_at ASC)
    WHERE service_tier = 'PERSONAL_25';

CREATE TABLE search_request_window (
    search_request_id UUID NOT NULL,
    window_order INTEGER NOT NULL,
    window_start TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    window_end TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    PRIMARY KEY (search_request_id, window_order),
    CONSTRAINT fk_search_window_request FOREIGN KEY (search_request_id) REFERENCES search_request (id) ON DELETE CASCADE,
    CONSTRAINT ck_search_window_range CHECK (window_end > window_start)
);

CREATE TABLE appointment_slot (
    id UUID PRIMARY KEY,
    practice_id UUID NOT NULL,
    specialty VARCHAR(50) NOT NULL,
    appointment_type VARCHAR(60) NOT NULL,
    start_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    duration_minutes INTEGER NOT NULL,
    accepts_new_patients BOOLEAN NOT NULL,
    calendar_source VARCHAR(40) NOT NULL,
    external_calendar_reference VARCHAR(120) NOT NULL,
    treatment_cost_information VARCHAR(300) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_appointment_slot_practice FOREIGN KEY (practice_id) REFERENCES practice (id),
    CONSTRAINT ck_appointment_slot_duration CHECK (duration_minutes BETWEEN 1 AND 480),
    CONSTRAINT ck_appointment_slot_status CHECK (status IN ('AVAILABLE', 'OFFERED', 'BOOKED', 'CANCELLED')),
    CONSTRAINT ck_appointment_slot_calendar CHECK (calendar_source IN ('LOCAL_DEMO'))
);

CREATE INDEX ix_appointment_slot_practice ON appointment_slot (practice_id, start_at DESC);
CREATE INDEX ix_appointment_slot_available ON appointment_slot (start_at ASC) WHERE status = 'AVAILABLE';

CREATE TABLE appointment_slot_billing (
    appointment_slot_id UUID NOT NULL,
    billing_type VARCHAR(30) NOT NULL,
    PRIMARY KEY (appointment_slot_id, billing_type),
    CONSTRAINT fk_slot_billing_slot FOREIGN KEY (appointment_slot_id) REFERENCES appointment_slot (id) ON DELETE CASCADE,
    CONSTRAINT ck_slot_billing_type CHECK (billing_type IN ('STATUTORY', 'PRIVATE', 'SELF_PAY'))
);

CREATE TABLE appointment_offer (
    id UUID PRIMARY KEY,
    appointment_slot_id UUID NOT NULL,
    search_request_id UUID NOT NULL,
    offered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_offer_slot FOREIGN KEY (appointment_slot_id) REFERENCES appointment_slot (id),
    CONSTRAINT fk_offer_request FOREIGN KEY (search_request_id) REFERENCES search_request (id),
    CONSTRAINT ck_offer_expiry CHECK (expires_at > offered_at),
    CONSTRAINT ck_offer_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN'))
);

CREATE UNIQUE INDEX uk_offer_one_pending_per_slot
    ON appointment_offer (appointment_slot_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uk_offer_one_pending_per_request
    ON appointment_offer (search_request_id) WHERE status = 'PENDING';
CREATE INDEX ix_offer_request_history ON appointment_offer (search_request_id, offered_at DESC);
CREATE INDEX ix_offer_practice_history ON appointment_offer (appointment_slot_id, offered_at DESC);
CREATE INDEX ix_offer_expiry ON appointment_offer (expires_at) WHERE status = 'PENDING';

CREATE TABLE booking (
    id UUID PRIMARY KEY,
    appointment_offer_id UUID NOT NULL,
    appointment_slot_id UUID NOT NULL,
    search_request_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    booked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_booking_offer FOREIGN KEY (appointment_offer_id) REFERENCES appointment_offer (id),
    CONSTRAINT fk_booking_slot FOREIGN KEY (appointment_slot_id) REFERENCES appointment_slot (id),
    CONSTRAINT fk_booking_request FOREIGN KEY (search_request_id) REFERENCES search_request (id),
    CONSTRAINT uk_booking_offer UNIQUE (appointment_offer_id),
    CONSTRAINT uk_booking_slot UNIQUE (appointment_slot_id),
    CONSTRAINT ck_booking_status CHECK (status IN ('CONFIRMED', 'CANCELLED_BY_PRACTICE'))
);

CREATE INDEX ix_booking_request_history ON booking (search_request_id, booked_at DESC);
CREATE INDEX ix_booking_slot ON booking (appointment_slot_id);
CREATE UNIQUE INDEX uk_booking_one_confirmed_per_request
    ON booking (search_request_id) WHERE status = 'CONFIRMED';

CREATE TABLE portal_fee (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    amount_cents INTEGER NOT NULL,
    currency VARCHAR(3) NOT NULL,
    service_tier VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_reference VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    status_changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_portal_fee_booking FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT uk_portal_fee_booking UNIQUE (booking_id),
    CONSTRAINT uk_portal_fee_provider_reference UNIQUE (provider_reference),
    CONSTRAINT ck_portal_fee_amount CHECK (amount_cents IN (500, 2500)),
    CONSTRAINT ck_portal_fee_currency CHECK (currency = 'EUR'),
    CONSTRAINT ck_portal_fee_tier CHECK (service_tier IN ('AUTOMATIC_5', 'PERSONAL_25')),
    CONSTRAINT ck_portal_fee_status CHECK (status IN ('OPEN', 'PAID', 'VOID', 'REFUND_PENDING', 'REFUNDED'))
);

CREATE TABLE practice_contact (
    id UUID PRIMARY KEY,
    search_request_id UUID NOT NULL,
    practice_id UUID,
    practice_name VARCHAR(180) NOT NULL,
    contacted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    channel VARCHAR(30) NOT NULL,
    response VARCHAR(40) NOT NULL,
    notes VARCHAR(1000) NOT NULL,
    created_by_username VARCHAR(100) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_practice_contact_request FOREIGN KEY (search_request_id) REFERENCES search_request (id),
    CONSTRAINT fk_practice_contact_practice FOREIGN KEY (practice_id) REFERENCES practice (id),
    CONSTRAINT ck_practice_contact_channel CHECK (channel IN ('PHONE', 'EMAIL', 'FAX')),
    CONSTRAINT ck_practice_contact_response CHECK (response IN ('NO_RESPONSE', 'DECLINED', 'AVAILABLE', 'CALLBACK'))
);

CREATE INDEX ix_practice_contact_request ON practice_contact (search_request_id, contacted_at DESC);
