package cl.thereyesrecords.disquera.controller;

import cl.thereyesrecords.disquera.dto.LoginRequest;
import cl.thereyesrecords.disquera.dto.TokenGoogleRequest;
import cl.thereyesrecords.disquera.model.Usuario;
import cl.thereyesrecords.disquera.repository.UsuarioRepository;
import cl.thereyesrecords.disquera.service.GoogleAuthService;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/usuarios")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final GoogleAuthService googleAuthService;

    private final HttpSessionSecurityContextRepository
            securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            GoogleAuthService googleAuthService) {

        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.googleAuthService = googleAuthService;
    }
    private Map<String, Object> datosDelUsuario(Usuario usuario) {

        Map<String, Object> respuesta = new LinkedHashMap<>();
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
        respuesta.put("mensaje", "Sesion activa");
        return respuesta;
    }
    private void abrirSesion(
            Usuario usuario,
            HttpServletRequest request,
            HttpServletResponse response) {

        String rol = usuario.getRol()
                .toUpperCase(Locale.ROOT);

        SimpleGrantedAuthority autoridad =
                new SimpleGrantedAuthority(
                        "ROLE_" + rol
                );

        UsernamePasswordAuthenticationToken autenticacion =
                new UsernamePasswordAuthenticationToken(
                        usuario.getCorreo(),
                        null,
                        List.of(autoridad)
                );
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext contexto =
                SecurityContextHolder.createEmptyContext();

        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        securityContextRepository.saveContext(
                contexto,
                request,
                response
        );
    }
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest datos,
            HttpServletRequest request,
            HttpServletResponse response) {

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCase(datos.correo().trim())
                .orElse(null);

        if (usuario == null
                || !Boolean.TRUE.equals(usuario.getActivo())
                || !passwordEncoder.matches(
                        datos.contrasena(),
                        usuario.getPasswordHash())) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "mensaje",
                            "Correo o contraseña incorrectos"
                    ));
        }
        abrirSesion(usuario, request, response);

        Map<String, Object> respuesta =
                datosDelUsuario(usuario);

        respuesta.put(
                "mensaje",
                "Credenciales correctas"
        );
        return ResponseEntity.ok(respuesta);
    }
    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> loginConGoogle(
            @Valid @RequestBody TokenGoogleRequest datos,
            HttpServletRequest request,
            HttpServletResponse response) {

        GoogleIdToken.Payload payload;
        try {
            payload = googleAuthService.verificarToken(datos.token());
        } catch (Exception ex) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "mensaje",
                            "Token de Google invalido o expirado"
                    ));
        }

        if (payload.getEmail() == null
                || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "mensaje",
                            "El correo de Google no esta verificado"
                    ));
        }

        String correo = payload.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCase(correo)
                .orElse(null);

        if (usuario == null) {
            String nombre = (String) payload.get("given_name");
            String apellido = (String) payload.get("family_name");

            usuario = new Usuario();
            usuario.setNombre(
                    nombre != null && !nombre.isBlank()
                            ? nombre : "Usuario");
            usuario.setApellido(
                    apellido != null && !apellido.isBlank()
                            ? apellido : "Google");
            usuario.setCorreo(correo);
            usuario.setPasswordHash(
                    passwordEncoder.encode(UUID.randomUUID().toString()));
            usuario.setRol("cliente");
            usuario.setActivo(true);
            usuario = usuarioRepository.save(usuario);
        }

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "mensaje",
                            "El usuario se encuentra desactivado"
                    ));
        }

        abrirSesion(usuario, request, response);

        Map<String, Object> respuesta = datosDelUsuario(usuario);
        respuesta.put("mensaje", "Credenciales correctas");
        return ResponseEntity.ok(respuesta);
    }
    @GetMapping("/sesion")
    public ResponseEntity<Map<String, Object>> obtenerSesion(
            Authentication autenticacion) {

        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(
                        autenticacion.getPrincipal())) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "mensaje",
                            "No hay una sesion iniciada"
                    ));
        }
        String correo = autenticacion.getName();
        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCase(correo)
                .orElse(null);

        if (usuario == null
                || !Boolean.TRUE.equals(usuario.getActivo())) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "mensaje",
                            "La sesion ya no es valida"
                    ));
        }
        return ResponseEntity.ok(
                datosDelUsuario(usuario)
        );
    }
}