package com.javatodev.finance.service;

import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

/**
 * Interface for Keycloak user management operations (OCP + DIP).
 * Decouples user service logic from the Keycloak admin client implementation.
 */
public interface IKeycloakUserService {
    Integer createUser(UserRepresentation userRepresentation);
    void updateUser(UserRepresentation userRepresentation);
    List<UserRepresentation> readUserByEmail(String email);
    UserRepresentation readUser(String authId);
}
