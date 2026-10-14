
package cl.thereyesrecords.disquera.repository;

import cl.thereyesrecords.disquera.model.Pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    // Buscar pedidos de un usuario
    List<Pedido> findByUsuarioId(Long usuarioId);

    // Buscar pedidos por estado del pago
    List<Pedido> findByEstado(String estado);

    // Mostrar los pedidos mas recientes primero
    List<Pedido> findAllByOrderByFechaPedidoDesc();
}
