package com.kiefer.arzttermin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "practice_contact")
public class PracticeContact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_request_id", nullable = false)
    private SearchRequest searchRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practice_id")
    private Practice practice;

    @Column(name = "practice_name", nullable = false, length = 180)
    private String practiceName;

    @Column(name = "contacted_at", nullable = false)
    private Instant contactedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private ContactChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "response", nullable = false, length = 40)
    private ContactResponse response;

    @Column(name = "notes", nullable = false, length = 1_000)
    private String notes;

    @Column(name = "created_by_username", nullable = false, length = 100)
    private String createdByUsername;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PracticeContact() {
    }

    public PracticeContact(SearchRequest searchRequest, Practice practice, String practiceName,
                           Instant contactedAt, ContactChannel channel, ContactResponse response,
                           String notes, String createdByUsername) {
        this.searchRequest = Objects.requireNonNull(searchRequest, "searchRequest");
        if (searchRequest.getServiceTier() != ServiceTier.PERSONAL_25) {
            throw new IllegalArgumentException("Contacts can only be recorded for personal searches");
        }
        this.practice = practice;
        this.practiceName = requireText(practice != null ? practice.getName() : practiceName, "practiceName");
        this.contactedAt = Objects.requireNonNull(contactedAt, "contactedAt");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.response = Objects.requireNonNull(response, "response");
        this.notes = notes == null ? "" : notes.trim();
        this.createdByUsername = requireText(createdByUsername, "createdByUsername");
    }

    public UUID getId() { return id; }
    public SearchRequest getSearchRequest() { return searchRequest; }
    public Practice getPractice() { return practice; }
    public String getPracticeName() { return practiceName; }
    public Instant getContactedAt() { return contactedAt; }
    public ContactChannel getChannel() { return channel; }
    public ContactResponse getResponse() { return response; }
    public String getNotes() { return notes; }
    public String getCreatedByUsername() { return createdByUsername; }
    public long getVersion() { return version; }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
