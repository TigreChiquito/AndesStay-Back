package cl.tigrechiquito.ms_andesstay_bff.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * El BFF es un Resource Server: valida el JWT que emite Azure AD y aplica
 * autorización por rol antes de enrutar hacia los micros.
 *
 * Los roles vienen en el claim "roles" del token (App Roles de Azure AD). Se
 * mapean a authorities ROLE_<valor>. Los valores (ADMIN, OPERADOR, HUESPED,
 * AUDITOR) deben coincidir con los App Roles definidos en el App Registration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API stateless con token: sin CSRF ni sesión.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // catalog: leer lo puede cualquiera autenticado; escribir solo Admin
                        .requestMatchers(HttpMethod.GET, "/api/units/**").authenticated()
                        .requestMatchers("/api/units/**").hasRole("ADMIN")

                        // reservations: crear (huésped/operador/admin), cambiar estado (operador/admin), leer (autenticado)
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasAnyRole("HUESPED", "OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/*/status").hasAnyRole("OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").authenticated()

                        // report: KPIs solo Admin
                        .requestMatchers("/api/reports/**").hasRole("ADMIN")

                        // audit: solo lectura para Auditor (o Admin)
                        .requestMatchers("/api/audit/**").hasAnyRole("AUDITOR", "ADMIN")

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /** Extrae los App Roles del claim "roles" y los convierte en ROLE_*. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
            return authorities;
        });
        return converter;
    }
}