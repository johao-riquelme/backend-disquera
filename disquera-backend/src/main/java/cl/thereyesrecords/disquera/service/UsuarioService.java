package cl.thereyesrecords.disquera.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import cl.thereyesrecords.disquera.model.Usuario;
import cl.thereyesrecords.disquera.repository.UsuarioRepository;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    public UsuarioService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }
    public Usuario registrarCliente(
            String nombre,
            String apellido,
            String correo,
            String contrasena,
            String telefono,
            String direccion) {

        if (nombre == null || nombre.isBlank() ||
            apellido == null || apellido.isBlank() ||
            correo == null || correo.isBlank() ||
            contrasena == null || contrasena.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Completa los campos obligatorios"
            );
        }
        if (contrasena.length() < 8) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La contraseña debe tener al menos 8 caracteres"
            );
        }
        String correoLimpio = correo.trim()
                .toLowerCase(Locale.ROOT);

        if (usuarioRepository.existsByCorreoIgnoreCase(correoLimpio)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El correo ya está registrado"
            );
        }
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre.trim());
        usuario.setApellido(apellido.trim());
        usuario.setCorreo(correoLimpio);

        usuario.setPasswordHash(
            passwordEncoder.encode(contrasena)
        );

        usuario.setTelefono(telefono);
        usuario.setDireccion(direccion);
        usuario.setRol("cliente");
        usuario.setActivo(true);

        return usuarioRepository.save(usuario);
    }
}
