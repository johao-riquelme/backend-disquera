
package cl.thereyesrecords.disquera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.thereyesrecords.disquera.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Buscar un usuario por correo
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    // Comprobar si el correo ya está registrado
    boolean existsByCorreoIgnoreCase(String correo);

    // Buscar usuarios según su rol
    List<Usuario> findByRol(String rol);

}
