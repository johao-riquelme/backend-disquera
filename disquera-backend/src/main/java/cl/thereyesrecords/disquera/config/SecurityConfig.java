package cl.thereyesrecords.disquera.config;

import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.IF_REQUIRED
                )
            )
            .securityContext(context -> context
                .securityContextRepository(
                    new HttpSessionSecurityContextRepository()
                )
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(
                    "/api/usuarios/registro",
                    "/api/usuarios/login",
                    "/api/usuarios/logout",
                    "/api/pedidos"
                )
            )
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll()
                .requestMatchers("/error").permitAll()

                .requestMatchers(
                    HttpMethod.GET,
                    "/api/productos",
                    "/api/productos/*"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/usuarios/registro"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/usuarios/login"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.POST,
                    "/api/pedidos"
                ).permitAll()

                .anyRequest().authenticated()
            )


            .logout(logout -> logout

                .logoutUrl("/api/usuarios/logout")

                .invalidateHttpSession(true)

                .clearAuthentication(true)

                .deleteCookies("JSESSIONID")

                .logoutSuccessHandler(
                    (request, response, authentication) -> {
                        response.setStatus(200);
                        response.setContentType(
                            "application/json;charset=UTF-8"
                        );
                        response.getWriter().write(
                            "{\"mensaje\":\"Sesion cerrada correctamente\"}"
                        );
                    }
                )
                .permitAll()
            )
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());
        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5174"
        ));
        config.setAllowedMethods(List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
        ));
        config.setAllowedHeaders(List.of(
            "Content-Type",
            "Authorization",
            "X-CSRF-TOKEN"
        ));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(
            "/api/**",
            config
        );
        return source;
    }
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
