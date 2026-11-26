package com.kiefer.arzttermin.config;

import com.kiefer.arzttermin.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local pilot security. The accounts intentionally contain synthetic demo data only.
 */
@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig {

    public static final String DEMO_PASSWORD = "demo";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer
                .loginView(LoginView.class));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        String password = passwordEncoder.encode(DEMO_PASSWORD);
        return new InMemoryUserDetailsManager(
                User.withUsername("patient.anna").password(password).roles("PATIENT").build(),
                User.withUsername("patient.ben").password(password).roles("PATIENT").build(),
                User.withUsername("patient.clara").password(password).roles("PATIENT").build(),
                User.withUsername("praxis.haut").password(password).roles("PRACTICE").build(),
                User.withUsername("portal.mia").password(password).roles("STAFF").build()
        );
    }
}
