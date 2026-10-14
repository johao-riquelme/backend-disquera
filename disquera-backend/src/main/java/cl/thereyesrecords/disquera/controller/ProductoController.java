package cl.thereyesrecords.disquera.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.server.ResponseStatusException;

import cl.thereyesrecords.disquera.model.Producto;
import cl.thereyesrecords.disquera.service.ProductoService;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    private void verificarAdministrador(
            Authentication autenticacion) {

        if (autenticacion == null
                || !autenticacion.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Debes iniciar sesion"
            );
        }

        boolean esAdmin = autenticacion
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(rol -> rol.equals("ROLE_ADMIN"));

        if (!esAdmin) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo los administradores pueden realizar esta operacion"
            );
        }
    }

    @GetMapping
    public List<Producto> listar() {

        return productoService.listar();
    }

    @GetMapping("/{id}")
    public Producto buscarPorId(@PathVariable Long id) {

        Producto producto = productoService.buscarPorId(id);

        if (!Boolean.TRUE.equals(producto.getActivo())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no encontrado"
            );
        }

        return producto;
    }
    @PostMapping
    public ResponseEntity<Producto> crear(
            @RequestBody Producto producto,
            Authentication autenticacion) {

        verificarAdministrador(autenticacion);

        Producto nuevoProducto =
                productoService.guardar(producto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(nuevoProducto);
    }
    @PutMapping("/{id}")
    public ResponseEntity<Producto> actualizar(
            @PathVariable Long id,
            @RequestBody Producto datos,
            Authentication autenticacion) {

        verificarAdministrador(autenticacion);

        Producto productoActualizado =
                productoService.actualizar(id, datos);

        return ResponseEntity.ok(productoActualizado);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> desactivar(
            @PathVariable Long id,
            Authentication autenticacion) {

        verificarAdministrador(autenticacion);

        productoService.desactivar(id);

        return ResponseEntity.ok(
                Map.of(
                        "mensaje",
                        "Producto desactivado correctamente"
                )
        );
    }
}
