package vn.vshop.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication
public class SecurityConfiguration {
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
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/v1/catalog/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/seller/**")
                                        .hasAnyRole("SELLER", "BUYER")
                                        .requestMatchers("/api/v1/cart/**", "/api/v1/checkout/**")
                                        .hasAnyRole("BUYER", "SELLER")
                                        .requestMatchers("/api/v1/me")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .oauth2ResourceServer(
                        oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
