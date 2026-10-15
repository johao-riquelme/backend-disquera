package cl.thereyesrecords.disquera.service;

import cl.thereyesrecords.disquera.dto.PedidoRequest;

import cl.thereyesrecords.disquera.model.Pedido;
import cl.thereyesrecords.disquera.model.DetallePedido;
import cl.thereyesrecords.disquera.model.DetallePlanPedido;
import cl.thereyesrecords.disquera.model.Producto;
import cl.thereyesrecords.disquera.model.Plan;
import cl.thereyesrecords.disquera.model.Usuario;

import cl.thereyesrecords.disquera.repository.PedidoRepository;
import cl.thereyesrecords.disquera.repository.DetallePedidoRepository;
import cl.thereyesrecords.disquera.repository.DetallePlanPedidoRepository;
import cl.thereyesrecords.disquera.repository.PlanRepository;
import cl.thereyesrecords.disquera.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final DetallePlanPedidoRepository detallePlanPedidoRepository;
    private final PlanRepository planRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    private record ClaveItem(String tipo, Long id) {
    }

    public PedidoService(
            PedidoRepository pedidoRepository,
            DetallePedidoRepository detallePedidoRepository,
            DetallePlanPedidoRepository detallePlanPedidoRepository,
            PlanRepository planRepository,
            UsuarioRepository usuarioRepository,
            EntityManager entityManager) {

        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.detallePlanPedidoRepository = detallePlanPedidoRepository;
        this.planRepository = planRepository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
    }
    private Long obtenerIdUsuarioAutenticado() {

        Authentication autenticacion =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(
                        autenticacion.getPrincipal())) {
            return null;
        }

        String correo = autenticacion.getName();

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCase(correo)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "La sesion del usuario no es valida"
                        )
                );

        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El usuario se encuentra desactivado"
            );
        }

        return usuario.getId();
    }

    private String obtenerTipo(PedidoRequest.Item item) {

        if (item.tipo() == null) {
            return "producto";
        }

        String tipo = item.tipo()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!tipo.equals("producto")
                && !tipo.equals("plan")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tipo de articulo no valido"
            );
        }

        return tipo;
    }
    @Transactional
    public Pedido crearPedido(PedidoRequest solicitud) {

        if (solicitud == null
                || solicitud.cliente() == null
                || solicitud.items() == null
                || solicitud.items().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El pedido debe incluir cliente y articulos"
            );
        }

        PedidoRequest.Cliente cliente = solicitud.cliente();

        if (cliente.nombre() == null
                || cliente.nombre().isBlank()
                || cliente.apellido() == null
                || cliente.apellido().isBlank()
                || cliente.email() == null
                || cliente.email().isBlank()
                || cliente.direccion() == null
                || cliente.direccion().isBlank()
                || cliente.comuna() == null
                || cliente.comuna().isBlank()
                || cliente.region() == null
                || cliente.region().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Faltan datos obligatorios del cliente"
            );
        }

        Long usuarioId = obtenerIdUsuarioAutenticado();
        Map<ClaveItem, Integer> cantidades =
                new LinkedHashMap<>();

        try {

            for (PedidoRequest.Item item : solicitud.items()) {

                if (item == null
                        || item.id() == null
                        || item.id() <= 0
                        || item.cantidad() == null
                        || item.cantidad() <= 0) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Los articulos deben tener ID y cantidad validos"
                    );
                }

                String tipo = obtenerTipo(item);

                ClaveItem clave = new ClaveItem(
                        tipo,
                        item.id()
                );

                cantidades.merge(
                        clave,
                        item.cantidad(),
                        Math::addExact
                );
            }

        } catch (ArithmeticException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La cantidad solicitada es demasiado grande"
            );
        }

        List<ClaveItem> clavesProductos =
                cantidades.keySet()
                        .stream()
                        .filter(clave ->
                                clave.tipo().equals("producto"))
                        .sorted(Comparator.comparing(ClaveItem::id))
                        .toList();

        List<ClaveItem> clavesPlanes =
                cantidades.keySet()
                        .stream()
                        .filter(clave ->
                                clave.tipo().equals("plan"))
                        .sorted(Comparator.comparing(ClaveItem::id))
                        .toList();

        Map<ClaveItem, Producto> productos =
                new LinkedHashMap<>();

        Map<ClaveItem, Plan> planes =
                new LinkedHashMap<>();

        Map<ClaveItem, Long> subtotales =
                new LinkedHashMap<>();

        long total = 0L;

        try {

            for (ClaveItem clave : clavesProductos) {

                Producto producto = entityManager.find(
                        Producto.class,
                        clave.id(),
                        LockModeType.PESSIMISTIC_WRITE
                );

                if (producto == null
                        || !Boolean.TRUE.equals(
                                producto.getActivo())) {

                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Producto no encontrado: " + clave.id()
                    );
                }

                int cantidad = cantidades.get(clave);

                int stockDisponible =
                        producto.getStock() == null
                                ? 0
                                : producto.getStock();

                if (stockDisponible < cantidad) {

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Stock insuficiente para: "
                                    + producto.getNombre()
                    );
                }

                if (producto.getPrecio() == null
                        || producto.getPrecio() < 0) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Precio invalido para: "
                                    + producto.getNombre()
                    );
                }
                long subtotal = Math.multiplyExact(
                        producto.getPrecio().longValue(),
                        (long) cantidad
                );
                total = Math.addExact(total, subtotal);
                productos.put(clave, producto);
                subtotales.put(clave, subtotal);
            }

            for (ClaveItem clave : clavesPlanes) {

                Plan plan = planRepository
                        .findById(clave.id())
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Plan no encontrado: " + clave.id()
                                )
                        );

                if (!Boolean.TRUE.equals(plan.getActivo())) {

                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "El plan no esta disponible: "
                                    + clave.id()
                    );
                }
                if (plan.getPrecio() == null
                        || plan.getPrecio() <= 0) {

                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Precio invalido para: "
                                    + plan.getNombre()
                    );
                }
                int cantidad = cantidades.get(clave);

                long subtotal = Math.multiplyExact(
                        plan.getPrecio().longValue(),
                        (long) cantidad
                );
                total = Math.addExact(total, subtotal);
                planes.put(clave, plan);
                subtotales.put(clave, subtotal);
            }

        } catch (ArithmeticException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El total del pedido excede el limite permitido"
            );
        }

        String nombreCompleto =
                cliente.nombre().trim()
                        + " "
                        + cliente.apellido().trim();
        String correo = cliente.email()
                .trim()
                .toLowerCase(Locale.ROOT);
        if (nombreCompleto.length() > 150
                || correo.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nombre o correo demasiado largo"
            );
        }
        String direccionCompleta =
                cliente.direccion().trim()
                        + ", "
                        + cliente.comuna().trim()
                        + ", "
                        + cliente.region().trim();

        Pedido pedido = new Pedido();

        pedido.setUsuarioId(usuarioId);
        pedido.setNombreCliente(nombreCompleto);
        pedido.setCorreoCliente(correo);
        pedido.setDireccionEnvio(direccionCompleta);
        pedido.setTelefono(null);
        pedido.setTotal(total);
        pedido.setMedioPago("simulado");
        pedido.setEstado("aprobado_simulado");
        Pedido pedidoGuardado =
                pedidoRepository.save(pedido);

        List<DetallePedido> detallesProductos =
                new ArrayList<>();

        for (Map.Entry<ClaveItem, Producto> entrada
                : productos.entrySet()) {
            ClaveItem clave = entrada.getKey();
            Producto producto = entrada.getValue();
            int cantidad = cantidades.get(clave);
            DetallePedido detalle = new DetallePedido();
            detalle.setPedidoId(pedidoGuardado.getId());
            detalle.setProductoId(clave.id());
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setSubtotal(subtotales.get(clave));
            detallesProductos.add(detalle);
            producto.setStock(
                    producto.getStock() - cantidad
            );
        }
        if (!detallesProductos.isEmpty()) {
            detallePedidoRepository.saveAll(detallesProductos);
        }

        List<DetallePlanPedido> detallesPlanes =
                new ArrayList<>();
        for (Map.Entry<ClaveItem, Plan> entrada
                : planes.entrySet()) {
            ClaveItem clave = entrada.getKey();
            Plan plan = entrada.getValue();
            int cantidad = cantidades.get(clave);
            DetallePlanPedido detalle = new DetallePlanPedido();
            detalle.setPedidoId(pedidoGuardado.getId());
            detalle.setPlanId(clave.id());
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(plan.getPrecio());
            detalle.setSubtotal(subtotales.get(clave));

            detallesPlanes.add(detalle);
        }
        if (!detallesPlanes.isEmpty()) {
            detallePlanPedidoRepository.saveAll(detallesPlanes);
        }
        return pedidoGuardado;
    }
}
