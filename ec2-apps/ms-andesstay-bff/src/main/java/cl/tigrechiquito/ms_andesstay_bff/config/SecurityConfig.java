package cl.tigrechiquito.ms_andesstay_bff.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * El BFF es un Resource Server: valida el JWT de Azure AD y aplica autorizacion
 * por rol antes de enrutar hacia los micros. Ademas habilita CORS para que el
 * front (Angular + MSAL, en otro origen) pueda llamarlo desde el navegador.
 *
 * Los roles vienen en el claim "roles" (App Roles de Azure AD) -> ROLE_*.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())   // usa el CorsConfigurationSource de abajo
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // preflight CORS: el navegador manda OPTIONS sin token
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // catalog: leer autenticado; escribir solo Admin
                        .requestMatchers(HttpMethod.GET, "/api/units/**").authenticated()
                        .requestMatchers("/api/units/**").hasRole("ADMIN")

                        // reservations
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasAnyRole("HUESPED", "OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/reservations/*/status").hasAnyRole("OPERADOR", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/**").authenticated()

                        // report: solo Admin
                        .requestMatchers("/api/reports/**").hasRole("ADMIN")

                        // audit: solo lectura Auditor (o Admin)
                        .requestMatchers("/api/audit/**").hasAnyRole("AUDITOR", "ADMIN")

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /** Origenes del front permitidos (configurables por cors.allowed-origins). */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setAllowCredentials(false);   // el token va en el header Authorization, no en cookies
        config.setMaxAge(3600L);             // cachea el preflight 1 hora

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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