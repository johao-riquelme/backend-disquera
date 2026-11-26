package cl.thereyesrecords.disquera.controller;

import cl.thereyesrecords.disquera.dto.LoginRequest;
import cl.thereyesrecords.disquera.model.Usuario;
import cl.thereyesrecords.disquera.repository.UsuarioRepository;
import cl.thereyesrecords.disquera.service.GoogleAuthService;
import cl.thereyesrecords.disquera.service.JwtService;
import cl.thereyesrecords.disquera.service.RefreshTokenService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleAuthService googleAuthService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CookieCsrfTokenRepository csrfRepository;

    private static final String REFRESH_COOKIE = "refresh_token";

    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${google.auto-link.admin-ids:}")
    private String administradoresGoogle;

    public AuthController(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            GoogleAuthService googleAuthService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            CookieCsrfTokenRepository csrfRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.googleAuthService = googleAuthService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.csrfRepository = csrfRepository;
    }

    private Map<String, Object> datosDelUsuario(
            Usuario usuario
    ) {

        Map<String, Object> respuesta =
                new LinkedHashMap<>();

        respuesta.put("id", usuario.getId());
        respuesta.put("nombre", usuario.getNombre());

        respuesta.put(
                "apellido",
                usuario.getApellido() != null
                        ? usuario.getApellido()
                        : ""
        );

        respuesta.put("correo", usuario.getCorreo());

        respuesta.put(
                "direccion",
                usuario.getDireccion() != null
                        ? usuario.getDireccion()
                        : ""
        );

        respuesta.put(
                "telefono",
                usuario.getTelefono() != null
                        ? usuario.getTelefono()
                        : ""
        );

        respuesta.put("rol", usuario.getRol());

        respuesta.put(
                "googleVinculado",
                usuario.getGoogleSub() != null
        );

        respuesta.put("mensaje", "Sesion activa");

        return respuesta;
    }


    private Map<String, Object> respuestaConToken(
            Usuario usuario,
            String accessToken,
            String mensaje
    ) {

        Map<String, Object> respuesta =
                datosDelUsuario(usuario);

        respuesta.put("accessToken", accessToken);
        respuesta.put("tokenType", "Bearer");

        respuesta.put(
                "expiresIn",
                jwtService.getExpirationSeconds()
        );

        respuesta.put("mensaje", mensaje);

        return respuesta;
    }


    private void escribirCookie(
            HttpServletResponse response,
            String token,
            long maxAge
    ) {

        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/usuarios")
                .maxAge(maxAge)
                .build();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString()
        );
    }

    private ResponseEntity<Map<String, Object>> iniciarSesion(
            Usuario usuario,
            String refreshAnterior,
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        String accessToken =
                jwtService.generarToken(usuario);

        refreshTokenService.revocar(refreshAnterior);

        String refreshToken =
                refreshTokenService.generarRefreshToken(
                        usuario
                );

        escribirCookie(
                response,
                refreshToken,
                refreshTokenService.getExpirationSeconds()
        );

        csrfRepository.saveToken(
                null,
                request,
                response
        );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        respuestaConToken(
                                usuario,
                                accessToken,
                                "Credenciales correctas"
                        )
                );
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(

            @Valid @RequestBody LoginRequest datos,

            @CookieValue(
                    name = REFRESH_COOKIE,
                    required = false
            )
            String refreshAnterior,

            HttpServletRequest request,
            HttpServletResponse response
    ) {

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCase(
                        datos.correo().trim()
                )
                .orElse(null);

        if (usuario == null
                || !Boolean.TRUE.equals(usuario.getActivo())
                || usuario.getPasswordHash() == null
                || !passwordEncoder.matches(
                        datos.contrasena(),
                        usuario.getPasswordHash()
                )) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(
                            Map.of(
                                    "mensaje",
                                    "Correo o contraseña incorrectos"
                            )
                    );
        }

        return iniciarSesion(
                usuario,
                refreshAnterior,
                request,
                response
        );
    }

    public record SolicitudGoogle(

            @NotBlank
            @Size(max = 16384)
            String token,

            Boolean vincular,

            @Size(max = 255)
            String correo,

            @Size(max = 1024)
            String contrasena

    ) {

        @Override
        public String toString() {
            return "SolicitudGoogle[credenciales ocultas]";
        }
    }

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> loginConGoogle(

            @Valid @RequestBody SolicitudGoogle datos,

            @CookieValue(
                    name = REFRESH_COOKIE,
                    required = false
            )
            String refreshAnterior,

            HttpServletRequest request,
            HttpServletResponse response
    ) {

        GoogleIdToken.Payload payload;

        // Verificar autenticidad del token de Google.

        try {

            payload = googleAuthService.verificarToken(
                    datos.token()
            );

        } catch (Exception ex) {

            return errorGoogle(
                    HttpStatus.UNAUTHORIZED,
                    "Token de Google invalido o expirado"
            );
        }

        String sub = payload.getSubject();
        String email = payload.getEmail();

        // Verificar identidad y correo.

        if (sub == null
                || sub.isBlank()
                || sub.length() > 255
                || email == null
                || email.isBlank()
                || !Boolean.TRUE.equals(
                        payload.getEmailVerified()
                )) {

            return errorGoogle(
                    HttpStatus.UNAUTHORIZED,
                    "Identidad de Google no verificada"
            );
        }

        String correo = email.trim()
                .toLowerCase(Locale.ROOT);

        int arroba = correo.lastIndexOf('@');

        if (correo.length() > 255
                || arroba <= 0
                || arroba == correo.length() - 1) {

            return errorGoogle(
                    HttpStatus.FORBIDDEN,
                    "Correo no autorizado"
            );
        }

        String dominioCorreo =
                correo.substring(arroba + 1);

        Object hd = payload.get("hd");

        // Comprobar que Google acredita
        // la titularidad actual del correo.

        boolean correoAutorizado =
                "gmail.com".equals(dominioCorreo)

                || (hd instanceof String dominio
                    && dominioCorreo.equalsIgnoreCase(dominio));

        // Buscar primero por la identidad permanente.

        Usuario usuario = usuarioRepository
                .findByGoogleSub(sub)
                .orElse(null);

        // Si todavia no esta vinculado,
        // buscar por el correo verificado.

        if (usuario == null) {

            if (!correoAutorizado) {

                return errorGoogle(
                        HttpStatus.FORBIDDEN,
                        "Google no puede acreditar la titularidad de este correo"
                );
            }

            usuario = usuarioRepository
                    .findByCorreoIgnoreCase(correo)
                    .orElse(null);
            if (usuario != null) {

                if (!Boolean.TRUE.equals(usuario.getActivo())
                        || !rolGooglePermitido(usuario)) {

                    return errorGoogle(
                            HttpStatus.FORBIDDEN,
                            "Cuenta no autorizada"
                    );
                }

                // No reemplazar una identidad
                // de Google previamente vinculada.

                if (usuario.getGoogleSub() != null) {

                    return errorGoogle(
                            HttpStatus.CONFLICT,
                            "Esta cuenta tiene otra identidad de Google vinculada"
                    );
                }

                // Autorizar expresamente la vinculacion
                // automatica de administradores.

                if ("admin".equalsIgnoreCase(
                        usuario.getRol()
                ) && !administradorGoogleAutorizado(usuario)) {

                    return errorGoogle(
                            HttpStatus.FORBIDDEN,
                            "Administrador no autorizado para vinculacion automatica"
                    );
                }

                Long idOriginal = usuario.getId();

                // Vincular sin solicitar la contrasena local.
                // Conservar el usuario original y sus datos.

                try {

                    int actualizadas = usuarioRepository
                            .vincularGoogleSiDisponible(
                                    usuario.getId(),
                                    sub,
                                    usuario.getCorreo(),
                                    usuario.getPasswordHash(),
                                    usuario.getRol()
                            );

                    if (actualizadas != 1) {

                        return errorGoogle(
                                HttpStatus.CONFLICT,
                                "No se pudo vincular la cuenta; vuelve a intentarlo"
                        );
                    }

                } catch (DataIntegrityViolationException ex) {

                    return errorGoogle(
                            HttpStatus.CONFLICT,
                            "La identidad de Google ya esta vinculada"
                    );
                }

                // Recuperar el mismo usuario vinculado.

                usuario = usuarioRepository
                        .findByGoogleSub(sub)
                        .orElse(null);

                if (usuario == null
                        || !idOriginal.equals(usuario.getId())) {

                    return errorGoogle(
                            HttpStatus.CONFLICT,
                            "No se pudo comprobar la vinculacion"
                    );
                }

            }

            else {

                usuario = new Usuario();

                usuario.setNombre(
                        nombreGoogle(
                                payload.get("given_name"),
                                "Usuario"
                        )
                );

                usuario.setApellido(
                        nombreGoogle(
                                payload.get("family_name"),
                                "Google"
                        )
                );

                usuario.setCorreo(correo);

                usuario.setGoogleSub(sub);

                usuario.setPasswordHash(
                        passwordEncoder.encode(
                                UUID.randomUUID().toString()
                        )
                );

                // Los usuarios nuevos siempre son clientes.

                usuario.setRol("cliente");

                usuario.setActivo(true);

                try {

                    usuario = usuarioRepository
                            .saveAndFlush(usuario);

                } catch (DataIntegrityViolationException ex) {

                    return errorGoogle(
                            HttpStatus.CONFLICT,
                            "No se pudo registrar la cuenta"
                    );
                }
            }
        }

        if (!Boolean.TRUE.equals(usuario.getActivo())
                || !rolGooglePermitido(usuario)) {

            return errorGoogle(
                    HttpStatus.FORBIDDEN,
                    "Cuenta no autorizada para acceso con Google"
            );
        }

        // Impedir cambios automaticos del correo.

        if (!correo.equalsIgnoreCase(usuario.getCorreo())) {

            return errorGoogle(
                    HttpStatus.CONFLICT,
                    "El correo de Google no coincide con la cuenta vinculada"
            );
        }

        return iniciarSesion(
                usuario,
                refreshAnterior,
                request,
                response
        );
    }
    private boolean administradorGoogleAutorizado(
            Usuario usuario
    ) {

        String idAdministrador =
                String.valueOf(usuario.getId());

        return Arrays.stream(
                        administradoresGoogle.split(",")
                )
                .map(String::trim)
                .anyMatch(idAdministrador::equals);
    }

    private boolean rolGooglePermitido(
            Usuario usuario
    ) {

        return "cliente".equalsIgnoreCase(usuario.getRol())
                || "admin".equalsIgnoreCase(usuario.getRol());
    }

    private ResponseEntity<Map<String, Object>> errorGoogle(
            HttpStatus estado,
            String mensaje
    ) {

        return ResponseEntity
                .status(estado)
                .cacheControl(CacheControl.noStore())
                .body(
                        Map.of(
                                "mensaje",
                                mensaje
                        )
                );
    }

    private String nombreGoogle(
            Object valor,
            String alternativa
    ) {

        if (!(valor instanceof String nombre)
                || nombre.isBlank()) {

            return alternativa;
        }

        nombre = nombre.trim();

        int caracteres = nombre.codePointCount(
                0,
                nombre.length()
        );

        return caracteres <= 100
                ? nombre
                : nombre.substring(
                        0,
                        nombre.offsetByCodePoints(0, 100)
                );
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> renovarSesion(

            @CookieValue(
                    name = REFRESH_COOKIE,
                    required = false
            )
            String refreshToken,

            HttpServletResponse response
    ) {

        RefreshTokenService.TokenRenovado renovado =
                refreshTokenService.renovar(refreshToken);

        String accessToken =
                jwtService.generarToken(
                        renovado.usuario()
                );

        escribirCookie(
                response,
                renovado.refreshToken(),
                refreshTokenService.getExpirationSeconds()
        );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        respuestaConToken(
                                renovado.usuario(),
                                accessToken,
                                "Sesion renovada"
                        )
                );
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> cerrarSesion(

            @CookieValue(
                    name = REFRESH_COOKIE,
                    required = false
            )
            String refreshToken,

            HttpServletRequest request,
            HttpServletResponse response
    ) {

        refreshTokenService.revocar(refreshToken);

        escribirCookie(
                response,
                "",
                0
        );

        csrfRepository.saveToken(
                null,
                request,
                response
        );

        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        Map.of(
                                "mensaje",
                                "Sesion cerrada correctamente"
                        )
                );
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/sesion")
    public ResponseEntity<Map<String, Object>> obtenerSesion(

            Authentication autenticacion
    ) {

        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(
                        autenticacion.getPrincipal()
                )) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(
                            Map.of(
                                    "mensaje",
                                    "No hay una sesion iniciada"
                            )
                    );
        }

        String correo = autenticacion.getName();

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCase(correo)
                .orElse(null);

        if (usuario == null
                || !Boolean.TRUE.equals(usuario.getActivo())) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .cacheControl(CacheControl.noStore())
                    .body(
                            Map.of(
                                    "mensaje",
                                    "La sesion ya no es valida"
                            )
                    );
        }
        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        datosDelUsuario(usuario)
                );
    }
}
