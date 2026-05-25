package com.javatodev.finance.service;

import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.Collections;

/**
 * Factory for creating Keycloak UserRepresentation objects (Phase 10: Builder Pattern).
 * Extracts the inline construction logic from UserService into a reusable factory method.
 */
public class KeycloakUserRepresentationFactory {

    private KeycloakUserRepresentationFactory() {
        // prevent instantiation
    }

    /**
     * Creates a new Keycloak UserRepresentation with the provided details.
     * Sets email verification and account enabled status to false by default.
     */
    public static UserRepresentation createNewUser(String email, String firstName, String lastName, String password) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setEmail(email);
        userRepresentation.setEmailVerified(false);
        userRepresentation.setEnabled(false);
        userRepresentation.setUsername(email);
        userRepresentation.setFirstName(firstName);
        userRepresentation.setLastName(lastName);

        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
        credentialRepresentation.setValue(password);
        credentialRepresentation.setTemporary(false);
        userRepresentation.setCredentials(Collections.singletonList(credentialRepresentation));

        return userRepresentation;
    }
}
