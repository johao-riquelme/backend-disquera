package cl.thereyesrecords.disquera.config;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import cl.thereyesrecords.disquera.model.Plan;
import cl.thereyesrecords.disquera.repository.PlanRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DataInitializer.class);

    private record PlanInicial(Long id, String nombre, int precio) {
    }

    private static final List<PlanInicial> PLANES = List.of(
            new PlanInicial(1L, "Básico", 50_000),
            new PlanInicial(2L, "Premium", 70_000),
            new PlanInicial(3L, "Gold", 85_000),
            new PlanInicial(4L, "Deluxe", 180_000)
    );

    private final PlanRepository planRepository;

    public DataInitializer(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public void run(String... args) {
        int creados = 0;
        for (PlanInicial inicial : PLANES) {
            if (planRepository.existsById(inicial.id())) {
                continue;
            }
            Plan plan = new Plan();
            plan.setId(inicial.id());
            plan.setNombre(inicial.nombre());
            plan.setPrecio(inicial.precio());
            plan.setActivo(true);
            plan.setFechaCreacion(OffsetDateTime.now());
            planRepository.save(plan);
            creados++;
        }
        log.info("Datos iniciales: se crearon {} plan(es)", creados);
    }
}