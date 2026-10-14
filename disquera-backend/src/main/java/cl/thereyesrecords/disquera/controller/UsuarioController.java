
package cl.thereyesrecords.disquera.controller;

import cl.thereyesrecords.disquera.dto.RegistroRequest;
import cl.thereyesrecords.disquera.model.Usuario;
import cl.thereyesrecords.disquera.service.UsuarioService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Registrar un cliente nuevo
    @PostMapping("/registro")
    public ResponseEntity<Map<String, Object>> registrar(
            @Valid @RequestBody RegistroRequest datos) {

        Usuario usuario = usuarioService.registrarCliente(
                datos.nombre(),
                datos.apellido(),
                datos.correo(),
                datos.contrasena(),
                datos.telefono(),
                datos.direccion()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                        "id", usuario.getId(),
                        "nombre", usuario.getNombre(),
                        "correo", usuario.getCorreo(),
                        "rol", usuario.getRol()
                )
        );
    }
}
