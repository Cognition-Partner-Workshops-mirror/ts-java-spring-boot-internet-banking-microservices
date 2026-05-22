package com.casemanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Entity representing Case Data Set 1.
 * Stores case information received from the first set of API endpoints.
 * Maps to the CASE_DATA_SET1 table in Oracle database.
 */
@Entity
@Table(name = "CASE_DATA_SET1")
public class CaseDataSet1 {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "case_set1_seq")
    @SequenceGenerator(name = "case_set1_seq", sequenceName = "CASE_SET1_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    /** The case number identifier */
    @Column(name = "CASE_NUMBER", nullable = false, length = 50)
    private String caseNumber;

    /** Version number of the case data */
    @Column(name = "VERSION_NUMBER", nullable = false, length = 20)
    private String versionNumber;

    /** Name of the agent handling the case */
    @Column(name = "AGENT_NAME", nullable = false, length = 100)
    private String agentName;

    /** Party identifier associated with the case */
    @Column(name = "PARTY_IDENTIFIER", nullable = false, length = 100)
    private String partyIdentifier;

    /** The authenticated user from SM_USER header who submitted this data */
    @Column(name = "SM_USER", nullable = false, length = 100)
    private String smUser;

    /** Timestamp when the record was created */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Timestamp when the record was last updated */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    // Default constructor required by JPA
    public CaseDataSet1() {
    }

    // Parameterized constructor for convenience
    public CaseDataSet1(String caseNumber, String versionNumber, String agentName,
                        String partyIdentifier, String smUser) {
        this.caseNumber = caseNumber;
        this.versionNumber = versionNumber;
        this.agentName = agentName;
        this.partyIdentifier = partyIdentifier;
        this.smUser = smUser;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaseNumber() {
        return caseNumber;
    }

    public void setCaseNumber(String caseNumber) {
        this.caseNumber = caseNumber;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getPartyIdentifier() {
        return partyIdentifier;
    }

    public void setPartyIdentifier(String partyIdentifier) {
        this.partyIdentifier = partyIdentifier;
    }

    public String getSmUser() {
        return smUser;
    }

    public void setSmUser(String smUser) {
        this.smUser = smUser;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "CaseDataSet1{" +
                "id=" + id +
                ", caseNumber='" + caseNumber + '\'' +
                ", versionNumber='" + versionNumber + '\'' +
                ", agentName='" + agentName + '\'' +
                ", partyIdentifier='" + partyIdentifier + '\'' +
                ", smUser='" + smUser + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
