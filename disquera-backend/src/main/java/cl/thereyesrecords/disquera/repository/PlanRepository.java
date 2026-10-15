
package cl.thereyesrecords.disquera.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import cl.thereyesrecords.disquera.model.Plan;
@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByActivoTrue();
    List<Plan> findByNombreContainingIgnoreCase(String nombre);

}
