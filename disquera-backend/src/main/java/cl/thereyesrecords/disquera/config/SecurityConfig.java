package cl.thereyesrecords.disquera.config;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CookieCsrfTokenRepository csrfRepository) throws Exception {

        http
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())

            // Los endpoints de autenticacion con cookies conservan CSRF.
            // Los recursos que solo autorizan mediante Bearer no lo requieren.
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                    "/api/productos", "/api/productos/**",
                    "/api/admin/**",
                    "/api/pedidos", "/api/pedidos/**",
                    "/api/webpay/**"
                ))

            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                    "/v3/api-docs", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/csrf").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/usuarios/registro", "/api/usuarios/login",
                    "/api/usuarios/google", "/api/usuarios/refresh",
                    "/api/usuarios/logout").permitAll()

                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,
                    "/api/productos", "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,
                    "/api/productos", "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH,
                    "/api/productos", "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE,
                    "/api/productos", "/api/productos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,
                    "/api/productos", "/api/productos/*").permitAll()

                // La simulacion de pagos queda restringida a administradores.
                .requestMatchers("/api/webpay/simular-aprobacion").hasRole("ADMIN")
                // Pedidos y pagos requieren identidad autenticada.
                .requestMatchers("/api/pedidos", "/api/pedidos/**",
                    "/api/webpay/**").authenticated()
                .anyRequest().authenticated())

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))

            // El cierre de sesion lo administra AuthController.
            .logout(logout -> logout.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${jwt.cookie.secure:false}") boolean cookieSecure) {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(cookie -> cookie
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/"));
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173", "http://127.0.0.1:5173",
            "http://localhost:5174", "http://127.0.0.1:5174"));
        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Content-Type", "Authorization", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
