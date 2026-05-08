package com.javatodev.finance.configuration.filter;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds the authenticated user's ID for the current API request thread.
 */
@Getter
@Setter
public class ApiRequestContext {
    private String authId;
}
