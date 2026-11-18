package cl.thereyesrecords.disquera.config;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import cl.thereyesrecords.disquera.model.Plan;
import cl.thereyesrecords.disquera.model.Producto;
import cl.thereyesrecords.disquera.repository.PlanRepository;
import cl.thereyesrecords.disquera.repository.ProductoRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DataInitializer.class);

    private record PlanInicial(Long id, String nombre, int precio) {
    }

    private record ProductoInicial(
            String nombre,
            String descripcion,
            String categoria,
            int precio,
            int stock,
            String imagen) {
    }

    private static final List<PlanInicial> PLANES = List.of(
            new PlanInicial(1L, "Básico", 50_000),
            new PlanInicial(2L, "Premium", 70_000),
            new PlanInicial(3L, "Gold", 85_000),
            new PlanInicial(4L, "Deluxe", 180_000)
    );

    private static final List<ProductoInicial> PRODUCTOS = List.of(
            new ProductoInicial(
                    "Telefunken TF51",
                    "Micrófono para grabación de voces principales, guitarras acústicas y baterías.",
                    "microfono", 1_895_000, 12, "/micro/micro3.jpg"),
            new ProductoInicial(
                    "Neumann U 87 Ai",
                    "Micrófono de estudio para voces principales, locución e instrumentos. Cuenta con tres patrones polares.",
                    "microfono", 3_750_000, 13, "/micro/micro1.jpg"),
            new ProductoInicial(
                    "Apollo Twin X Gen 2",
                    "Interfaz de audio de escritorio para grabación y producción musical.",
                    "interfaz", 2_899_000, 10, "/interfaces/interfaz1.jpg"),
            new ProductoInicial(
                    "Manley Reference Cardioid",
                    "Micrófono para voces principales en producciones de pop, R&B, rap y música urbana.",
                    "microfono", 2_999_000, 12, "/micro/micro4.jpg"),
            new ProductoInicial(
                    "Apogee Symphony Desktop",
                    "Interfaz de audio con conversión de sonido de alta calidad en formato de escritorio.",
                    "interfaz", 1_499_000, 9, "/interfaces/interfaz2.jpg"),
            new ProductoInicial(
                    "AKG C414 XLII",
                    "Micrófono multipatrón para grabación de pianos, guitarras acústicas, coros y baterías.",
                    "microfono", 1_299_000, 15, "/micro/micro2.jpg")
    );

    private final PlanRepository planRepository;
    private final ProductoRepository productoRepository;

    public DataInitializer(
            PlanRepository planRepository,
            ProductoRepository productoRepository) {

        this.planRepository = planRepository;
        this.productoRepository = productoRepository;
    }

    @Override
    public void run(String... args) {
        sembrarPlanes();
        sembrarProductos();
    }

    private void sembrarPlanes() {
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

    /**
     * Solo carga el catalogo si la tabla productos esta vacia.
     * Si ya hay productos (aunque esten inactivos), no toca nada.
     */
    private void sembrarProductos() {
        if (productoRepository.count() > 0) {
            log.info("Datos iniciales: ya existen productos, no se agregan");
            return;
        }

        for (ProductoInicial inicial : PRODUCTOS) {
            Producto producto = new Producto();
            producto.setNombre(inicial.nombre());
            producto.setDescripcion(inicial.descripcion());
            producto.setCategoria(inicial.categoria());
            producto.setPrecio(inicial.precio());
            producto.setStock(inicial.stock());
            producto.setImagen(inicial.imagen());
            producto.setActivo(true);
            producto.setFechaCreacion(OffsetDateTime.now());
            productoRepository.save(producto);
        }
        log.info("Datos iniciales: se crearon {} producto(s)",
                PRODUCTOS.size());
    }
}