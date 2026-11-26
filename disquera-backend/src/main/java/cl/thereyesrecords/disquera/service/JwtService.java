package cl.thereyesrecords.disquera.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import org.springframework.web.server.ResponseStatusException;

import cl.thereyesrecords.disquera.model.Usuario;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String generarToken(Usuario usuario) {

        // Comprobar que el usuario se encuentra activo
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Usuario desactivado"
            );
        }

        Instant ahora = Instant.now();

        Instant fechaExpiracion =
                ahora.plusMillis(expiration);

        // Normalizar el rol obtenido desde la base de datos
        String rol = usuario.getRol() == null
                ? ""
                : usuario.getRol()
                    .trim()
                    .toUpperCase(Locale.ROOT);

        if (!List.of("ADMIN", "CLIENTE").contains(rol)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Rol de usuario no valido"
            );
        }

        // Construir el contenido del JWT
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(usuario.getCorreo())
                .issuedAt(ahora)
                .expiresAt(fechaExpiracion)
                .id(UUID.randomUUID().toString())
                .claim("usuarioId", usuario.getId())
                .claim("roles", List.of(rol))
                .build();

        // Configurar el algoritmo de firma
        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        // Firmar y generar el JWT
        return jwtEncoder.encode(
                JwtEncoderParameters.from(
                    header,
                    claims
                )
        ).getTokenValue();
    }

    public long getExpirationSeconds() {
        return expiration / 1000;
    }
}
