package com.kiefer.arzttermin.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void configuredPasswordReplacesDemoFallback() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        UserDetailsService users = config.userDetailsService(encoder, "configured-test-password");

        UserDetails patient = users.loadUserByUsername("patient.anna");

        assertTrue(encoder.matches("configured-test-password", patient.getPassword()));
        assertFalse(encoder.matches("demo", patient.getPassword()));
    }
}
