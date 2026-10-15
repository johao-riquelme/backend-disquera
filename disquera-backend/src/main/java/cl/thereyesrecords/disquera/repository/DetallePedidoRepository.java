
package cl.thereyesrecords.disquera.repository;

import cl.thereyesrecords.disquera.model.DetallePedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetallePedidoRepository
        extends JpaRepository<DetallePedido, Long> {
    List<DetallePedido> findByPedidoId(Long pedidoId);
    List<DetallePedido> findByProductoId(Long productoId);
}
