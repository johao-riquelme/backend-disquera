
package cl.thereyesrecords.disquera.repository;

import cl.thereyesrecords.disquera.model.DetallePedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetallePedidoRepository
        extends JpaRepository<DetallePedido, Long> {

    // Obtener los productos de un pedido
    List<DetallePedido> findByPedidoId(Long pedidoId);

    // Consultar los pedidos donde aparece un producto
    List<DetallePedido> findByProductoId(Long productoId);
}
