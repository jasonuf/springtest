package com.example.taskmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig — Spring Security Configuration
 * ================================================
 * This class defines the security rules for the application.
 * Without any security configuration, Spring Boot's auto-configuration
 * enables HTTP Basic authentication on ALL endpoints with a randomly
 * generated password printed to the console. That's too restrictive for
 * development (blocks Swagger UI and H2 console) and too weak for production.
 *
 * We override the auto-configuration with this explicit SecurityFilterChain.
 *
 * @Configuration — marks this as a source of Spring bean definitions.
 * @EnableWebSecurity — activates Spring Security's web security support and
 *   provides integration with Spring MVC. Required when customising security.
 * @EnableMethodSecurity — enables annotation-based method-level security.
 *   With this, you can protect individual service or controller methods with:
 *   @PreAuthorize("hasRole('ADMIN')")    — runs check BEFORE the method
 *   @PostAuthorize("returnObject.userId == authentication.name")  — runs AFTER
 *   @Secured("ROLE_USER")               — simpler role check (less expressive)
 *   Disabled by default — enable when OAuth2 is configured (see below).
 *
 * CURRENT STATE: Development / Open Mode
 *   All endpoints are open (permitAll) for ease of development.
 *   HTTP Basic is enabled as a fallback placeholder.
 *
 * EXTENDING TO OAUTH2 JWT:
 *   Step 1: Add the dependency to pom.xml (uncomment the oauth2-resource-server block)
 *   Step 2: Add to application.yml:
 *     spring:
 *       security:
 *         oauth2:
 *           resourceserver:
 *             jwt:
 *               issuer-uri: https://your-idp.example.com/realms/myrealm
 *   Step 3: Uncomment the oauth2ResourceServer line in securityFilterChain() below.
 *   Step 4: Remove or comment out the httpBasic() line.
 *   Step 5: Change anyRequest().permitAll() to anyRequest().authenticated()
 *   Step 6: Uncomment @EnableMethodSecurity above for method-level access control.
 *
 * OAUTH2 PROVIDER EXAMPLES:
 *   Keycloak (self-hosted):  issuer-uri: http://localhost:9090/realms/myrealm
 *   Auth0 (SaaS):            issuer-uri: https://YOUR_DOMAIN.auth0.com/
 *   Okta (SaaS):             issuer-uri: https://YOUR_DOMAIN.okta.com/oauth2/default
 *   AWS Cognito:             issuer-uri: https://cognito-idp.REGION.amazonaws.com/POOL_ID
 */
@Configuration
@EnableWebSecurity
// @EnableMethodSecurity  // Uncomment when OAuth2 is configured
public class SecurityConfig {

    /**
     * SecurityFilterChain — the primary security configuration bean.
     *
     * Spring Security's architecture is a chain of servlet filters. This bean
     * defines the filter chain for the main web application. The SecurityFilterChain
     * is built with a DSL (domain-specific language) using the HttpSecurity builder.
     *
     * Each .configure() call adds or modifies a filter in the chain.
     *
     * @Bean — Spring calls this method once and registers the return value
     * as a singleton bean. Security auto-configuration sees this bean and
     * backs off from its default configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── CSRF Protection ──────────────────────────────────────────
            // CSRF (Cross-Site Request Forgery) protection is important for
            // browser-based apps with session cookies. For stateless REST APIs
            // that use token authentication (OAuth2 JWT), CSRF is not a threat
            // because tokens are not automatically sent by browsers.
            //
            // We disable CSRF here because:
            //   1. We're a stateless REST API (no session cookies in production)
            //   2. The H2 console (a dev tool) doesn't include CSRF tokens
            //
            // WARNING: Do NOT disable CSRF if your API is consumed by browsers
            // using session-cookie authentication.
            .csrf(AbstractHttpConfigurer::disable)

            // ── Request Authorization Rules ───────────────────────────────
            // Rules are evaluated in ORDER — first matching rule wins.
            .authorizeHttpRequests(auth -> auth
                // Allow H2 web console (dev only — disabled in postgres profile)
                .requestMatchers("/h2-console/**").permitAll()
                // Allow Actuator health/info (needed for K8s probes without auth)
                .requestMatchers("/actuator/**").permitAll()
                // Allow Swagger UI and OpenAPI spec (for API exploration in dev)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // ── DEVELOPMENT MODE: allow all other requests ────────────
                // TODO: Change to .authenticated() when OAuth2 is configured
                .anyRequest().permitAll()
                // ── PRODUCTION MODE (uncomment when OAuth2 ready): ────────
                // .anyRequest().authenticated()
            )

            // ── OAuth2 Resource Server ────────────────────────────────────
            // Uncomment the block below to validate Bearer JWT tokens.
            // Spring will:
            //   1. Read the issuer-uri from application.yml
            //   2. Fetch the identity provider's JWKS (public signing keys)
            //   3. Validate incoming Bearer tokens against those keys
            //   4. Populate the SecurityContext with the authenticated principal
            //
            // .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

            // ── HTTP Basic (temporary dev fallback) ───────────────────────
            // Provides simple username/password authentication for dev/testing.
            // Spring Boot generates a password and prints it to the console.
            // Remove this line when OAuth2 is configured.
            .httpBasic(Customizer.withDefaults())

            // ── Frame Options ─────────────────────────────────────────────
            // H2 console is loaded in an <iframe>. By default, Spring Security
            // sets X-Frame-Options: DENY, which blocks iframes. We set it to
            // SAMEORIGIN to allow the H2 console iframe from the same origin.
            // Remove this for production (H2 console should not be accessible).
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
            );

        return http.build();
    }
}
