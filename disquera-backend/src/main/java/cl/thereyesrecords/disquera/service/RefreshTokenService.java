
package cl.thereyesrecords.disquera.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import cl.thereyesrecords.disquera.model.RefreshToken;
import cl.thereyesrecords.disquera.model.Usuario;
import cl.thereyesrecords.disquera.repository.RefreshTokenRepository;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    private final SecureRandom secureRandom =
            new SecureRandom();

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository) {

        this.refreshTokenRepository = refreshTokenRepository;
    }

    public record TokenRenovado(
            String refreshToken,
            Usuario usuario) {
    }

    // Generar un token aleatorio seguro
    private String generarTokenAleatorio() {

        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    // Calcular el hash SHA-256 del token
    private String calcularHash(String token) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                "No se pudo calcular el hash del token",
                e
            );
        }
    }

    // Crear y guardar un refresh token
    private String crearToken(Usuario usuario) {

        if (!Boolean.TRUE.equals(usuario.getActivo())) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Usuario desactivado"
            );
        }

        String tokenOriginal = generarTokenAleatorio();

        Instant ahora = Instant.now();

        RefreshToken refreshToken = new RefreshToken(
            usuario,
            calcularHash(tokenOriginal),
            ahora,
            ahora.plusMillis(refreshExpiration)
        );

        refreshTokenRepository.save(refreshToken);

        return tokenOriginal;
    }

    // Emitir un refresh token al iniciar sesion
    @Transactional
    public String generarRefreshToken(Usuario usuario) {

        return crearToken(usuario);
    }

    // Validar y renovar un refresh token
    @Transactional
    public TokenRenovado renovar(String tokenOriginal) {

        if (tokenOriginal == null
                || tokenOriginal.isBlank()) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token no disponible"
            );
        }

        RefreshToken token = refreshTokenRepository
                .findByTokenHash(calcularHash(tokenOriginal))
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Refresh token invalido"
                    )
                );

        if (token.isRevocado()
                || !token.getFechaExpiracion()
                         .isAfter(Instant.now())) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token expirado o revocado"
            );
        }

        Usuario usuario = token.getUsuario();

        if (!Boolean.TRUE.equals(usuario.getActivo())) {

            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Usuario desactivado"
            );
        }

        // Invalidar el token anterior
        token.setRevocado(true);

        refreshTokenRepository.save(token);

        // Generar un nuevo refresh token
        String nuevoToken = crearToken(usuario);

        return new TokenRenovado(
            nuevoToken,
            usuario
        );
    }

    // Revocar el token al cerrar sesion
    @Transactional
    public void revocar(String tokenOriginal) {

        if (tokenOriginal == null
                || tokenOriginal.isBlank()) {
            return;
        }

        refreshTokenRepository
            .findByTokenHash(calcularHash(tokenOriginal))
            .ifPresent(token -> {

                token.setRevocado(true);

                refreshTokenRepository.save(token);
            });
    }

    public long getExpirationSeconds() {

        return refreshExpiration / 1000;
    }
}
