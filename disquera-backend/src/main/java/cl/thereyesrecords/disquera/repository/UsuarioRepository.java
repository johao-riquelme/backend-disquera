
package cl.thereyesrecords.disquera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import cl.thereyesrecords.disquera.model.Usuario;

public interface UsuarioRepository
        extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByGoogleSub(String googleSub);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    List<Usuario> findByRol(String rol);

    // Vincular una cuenta sin sobrescribir una identidad existente.

    @Transactional
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            update Usuario u
            set u.googleSub = :sub
            where u.id = :id
              and u.googleSub is null
              and u.activo = true
              and u.correo = :correo
              and u.passwordHash = :hash
              and u.rol = :rol
            """)
    int vincularGoogleSiDisponible(
            @Param("id") Long id,
            @Param("sub") String sub,
            @Param("correo") String correo,
            @Param("hash") String hash,
            @Param("rol") String rol
    );

}
