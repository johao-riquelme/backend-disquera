package cl.thereyesrecords.disquera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cl.thereyesrecords.disquera.model.DetallePlanPedido;

@Repository
public interface DetallePlanPedidoRepository
        extends JpaRepository<DetallePlanPedido, Long> {
    List<DetallePlanPedido> findByPedidoId(Long pedidoId);
    List<DetallePlanPedido> findByPlanId(Long planId);

}
