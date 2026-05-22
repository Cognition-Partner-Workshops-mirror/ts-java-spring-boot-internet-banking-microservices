package com.casemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for receiving case data from API requests.
 * Used by both Set 1 and Set 2 endpoints as the input structure is identical.
 * Includes validation constraints to ensure data integrity before persistence.
 */
public class CaseDataRequest {

    /** Case number - required, max 50 characters */
    @NotBlank(message = "Case number is required")
    @Size(max = 50, message = "Case number must not exceed 50 characters")
    private String caseNumber;

    /** Version number - required, max 20 characters */
    @NotBlank(message = "Version number is required")
    @Size(max = 20, message = "Version number must not exceed 20 characters")
    private String versionNumber;

    /** Agent name - required, max 100 characters */
    @NotBlank(message = "Agent name is required")
    @Size(max = 100, message = "Agent name must not exceed 100 characters")
    private String agentName;

    /** Party identifier - required, max 100 characters */
    @NotBlank(message = "Party identifier is required")
    @Size(max = 100, message = "Party identifier must not exceed 100 characters")
    private String partyIdentifier;

    // Default constructor
    public CaseDataRequest() {
    }

    // Parameterized constructor
    public CaseDataRequest(String caseNumber, String versionNumber,
                           String agentName, String partyIdentifier) {
        this.caseNumber = caseNumber;
        this.versionNumber = versionNumber;
        this.agentName = agentName;
        this.partyIdentifier = partyIdentifier;
    }

    // Getters and Setters
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

    @Override
    public String toString() {
        return "CaseDataRequest{" +
                "caseNumber='" + caseNumber + '\'' +
                ", versionNumber='" + versionNumber + '\'' +
                ", agentName='" + agentName + '\'' +
                ", partyIdentifier='" + partyIdentifier + '\'' +
                '}';
    }
}
