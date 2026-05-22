package com.casemanagement.dto;

import java.time.LocalDateTime;

/**
 * DTO for sending case data responses back to the client.
 * Contains the persisted case data along with metadata like ID and timestamps.
 */
public class CaseDataResponse {

    private Long id;
    private String caseNumber;
    private String versionNumber;
    private String agentName;
    private String partyIdentifier;
    private String smUser;
    private String dataSet;
    private LocalDateTime createdAt;
    private String message;

    // Default constructor
    public CaseDataResponse() {
    }

    // Parameterized constructor for success responses
    public CaseDataResponse(Long id, String caseNumber, String versionNumber,
                            String agentName, String partyIdentifier, String smUser,
                            String dataSet, LocalDateTime createdAt, String message) {
        this.id = id;
        this.caseNumber = caseNumber;
        this.versionNumber = versionNumber;
        this.agentName = agentName;
        this.partyIdentifier = partyIdentifier;
        this.smUser = smUser;
        this.dataSet = dataSet;
        this.createdAt = createdAt;
        this.message = message;
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

    public String getDataSet() {
        return dataSet;
    }

    public void setDataSet(String dataSet) {
        this.dataSet = dataSet;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
