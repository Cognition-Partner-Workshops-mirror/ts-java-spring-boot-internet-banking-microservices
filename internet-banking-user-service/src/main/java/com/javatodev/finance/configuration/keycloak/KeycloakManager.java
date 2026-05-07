package com.javatodev.finance.configuration.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KeycloakManager {

    private final Keycloak keycloak;
    private final KeycloakProperties keycloakProperties;

    public RealmResource getKeyCloakInstanceWithRealm() {
        return keycloak.realm(keycloakProperties.getRealm());
    }

}
