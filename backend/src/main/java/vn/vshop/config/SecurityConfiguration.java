package vn.vshop.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication
public class SecurityConfiguration {
    /**
     * Set on the request when an ADMIN was refused only because the token lacks a second factor.
     */
    static final String MFA_MISSING = "vshop.mfa.missing";

    private final boolean adminMfaRequired;
    private final boolean apiDocsEnabled;

    SecurityConfiguration(
            @Value("${vshop.security.admin-mfa-required:true}") boolean adminMfaRequired,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) {
        this.adminMfaRequired = adminMfaRequired;
        this.apiDocsEnabled = apiDocsEnabled;
    }

    /**
     * ADMIN role plus proof of a second factor: Keycloak puts "otp" in the amr claim only when the
     * session passed the OTP form. A password-only admin token is refused even if the role is
     * right.
     */
    private AuthorizationDecision adminWithSecondFactor(
            java.util.function.Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        boolean admin =
                auth != null
                        && auth.isAuthenticated()
                        && auth.getAuthorities().stream()
                                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            return new AuthorizationDecision(false);
        }
        boolean otp =
                auth.getPrincipal() instanceof Jwt jwt
                        && jwt.getClaimAsStringList("amr") != null
                        && jwt.getClaimAsStringList("amr").contains("otp");
        if (adminMfaRequired && !otp) {
            context.getRequest().setAttribute(MFA_MISSING, Boolean.TRUE);
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(true);
    }

    private static void writeAccessDenied(
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException exception)
            throws java.io.IOException, jakarta.servlet.ServletException {
        if (request.getAttribute(MFA_MISSING) == null) {
            new AccessDeniedHandlerImpl().handle(request, response, exception);
            return;
        }
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        "{\"code\":\"MFA_REQUIRED\",\"message\":\"Cần xác thực hai bước (OTP) để dùng chức năng quản trị.\"}");
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    var authorities =
                            new ArrayList<org.springframework.security.core.GrantedAuthority>();
                    Map<String, Object> realm = jwt.getClaim("realm_access");
                    if (realm != null && realm.get("roles") instanceof List<?> roles) {
                        roles.stream()
                                .filter(String.class::isInstance)
                                .map(String.class::cast)
                                .filter(role -> List.of("BUYER", "SELLER", "ADMIN").contains(role))
                                .forEach(
                                        role ->
                                                authorities.add(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_" + role)));
                    }
                    return authorities;
                });
        return http
                // Bearer-only API. Browser cookies terminate at the Next.js BFF, which checks
                // Origin.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        // The OpenAPI document is public only while springdoc is
                                        // switched on (tests that generate it); otherwise it is
                                        // denied like any unlisted path.
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                apiDocsEnabled
                                                        ? "/v3/api-docs"
                                                        : "/v3/api-docs-disabled")
                                        .permitAll()
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/catalog/**")
                                        .permitAll()
                                        // Provider callback: no token, authenticated only by the
                                        // signature that PaymentService verifies.
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/payments/vnpay/ipn")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .access(this::adminWithSecondFactor)
                                        .requestMatchers("/api/v1/seller/**")
                                        .hasAnyRole("SELLER", "BUYER")
                                        .requestMatchers(
                                                "/api/v1/cart/**",
                                                "/api/v1/checkout/**",
                                                "/api/v1/orders/**",
                                                "/api/v1/orders")
                                        .hasAnyRole("BUYER", "SELLER")
                                        .requestMatchers(
                                                "/api/v1/me/addresses/**", "/api/v1/me/addresses")
                                        .hasAnyRole("BUYER", "SELLER")
                                        .requestMatchers("/api/v1/me")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        handling ->
                                handling.accessDeniedHandler(
                                        SecurityConfiguration::writeAccessDenied))
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
