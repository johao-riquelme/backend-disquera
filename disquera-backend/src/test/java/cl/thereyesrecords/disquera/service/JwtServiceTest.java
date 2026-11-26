
package cl.thereyesrecords.disquera.service;

import cl.thereyesrecords.disquera.model.Usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;

    private Usuario crearUsuario(String rol, boolean activo) {

        Usuario usuario = new Usuario();

        usuario.setId(999L);
        usuario.setNombre("Usuario");
        usuario.setApellido("Prueba");
        usuario.setCorreo("jwt.prueba@example.com");
        usuario.setRol(rol);
        usuario.setActivo(activo);

        return usuario;
    }

    // PRUEBA 1: Generacion y validacion de JWT.

    @Test
    void debeGenerarTokenValido() {

        Usuario usuario = crearUsuario("cliente", true);

        String token = jwtService.generarToken(usuario);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(3, token.split("\\.").length);

        Jwt jwt = jwtDecoder.decode(token);

        assertEquals(usuario.getCorreo(), jwt.getSubject());
        assertEquals(issuer, jwt.getClaimAsString("iss"));
        assertTrue(jwt.getAudience().contains(audience));

        Number usuarioId = jwt.getClaim("usuarioId");

        assertEquals(999L, usuarioId.longValue());

        assertEquals(
                List.of("CLIENTE"),
                jwt.getClaimAsStringList("roles")
        );
    }

    // PRUEBA 2: Normalizacion del rol administrador.

    @Test
    void debeNormalizarRolAdministrador() {

        Usuario usuario = crearUsuario(" admin ", true);

        String token = jwtService.generarToken(usuario);

        Jwt jwt = jwtDecoder.decode(token);

        assertEquals(
                List.of("ADMIN"),
                jwt.getClaimAsStringList("roles")
        );
    }

    // PRUEBA 3: Usuario desactivado.

    @Test
    void debeRechazarUsuarioDesactivado() {

        Usuario usuario = crearUsuario("cliente", false);

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> jwtService.generarToken(usuario)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                excepcion.getStatusCode()
        );
    }

    // PRUEBA 4: Rol no autorizado.

    @Test
    void debeRechazarRolInvalido() {

        Usuario usuario = crearUsuario("invitado", true);

        ResponseStatusException excepcion = assertThrows(
                ResponseStatusException.class,
                () -> jwtService.generarToken(usuario)
        );

        assertEquals(
                HttpStatus.FORBIDDEN,
                excepcion.getStatusCode()
        );
    }

    // PRUEBA 5: JWT manipulado.

    @Test
    void debeRechazarTokenManipulado() {

        Usuario usuario = crearUsuario("cliente", true);

        String token = jwtService.generarToken(usuario);

        String[] partes = token.split("\\.");

        String firma = partes[2];

        char nuevoCaracter =
                firma.charAt(0) == 'A' ? 'B' : 'A';

        String firmaManipulada =
                nuevoCaracter + firma.substring(1);

        String tokenManipulado =
                partes[0] + "."
                + partes[1] + "."
                + firmaManipulada;

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(tokenManipulado)
        );
    }

    // PRUEBA 6: Duracion y rechazo de JWT expirado.

    @Test
    void debeRechazarTokenExpirado() {

        Usuario usuario = crearUsuario("cliente", true);

        Jwt jwtValido = jwtDecoder.decode(
                jwtService.generarToken(usuario)
        );

        assertEquals(
                900L,
                jwtService.getExpirationSeconds()
        );

        assertEquals(
                900L,
                Duration.between(
                        jwtValido.getIssuedAt(),
                        jwtValido.getExpiresAt()
                ).getSeconds()
        );

        Instant ahora = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject("jwt.prueba@example.com")
                .issuedAt(ahora.minusSeconds(1200))
                .expiresAt(ahora.minusSeconds(300))
                .id(UUID.randomUUID().toString())
                .claim("usuarioId", 999L)
                .claim("roles", List.of("CLIENTE"))
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        String tokenExpirado = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(tokenExpirado)
        );
    }
}

