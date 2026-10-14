package cl.thereyesrecords.disquera.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.thereyesrecords.disquera.model.Producto;
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findByActivoTrue();
    List<Producto> findByCategoria(String categoria);
    List<Producto> findByCategoriaAndActivoTrue(String categoria);

}
