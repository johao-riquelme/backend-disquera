package cl.thereyesrecords.disquera.service;

import cl.thereyesrecords.disquera.dto.PedidoRequest;
import cl.thereyesrecords.disquera.model.Pedido;
import cl.thereyesrecords.disquera.model.DetallePedido;
import cl.thereyesrecords.disquera.model.Producto;
import cl.thereyesrecords.disquera.model.Usuario;

import cl.thereyesrecords.disquera.repository.PedidoRepository;
import cl.thereyesrecords.disquera.repository.DetallePedidoRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    public PedidoService(
            PedidoRepository pedidoRepository,
            DetallePedidoRepository detallePedidoRepository,
            UsuarioRepository usuarioRepository,
            EntityManager entityManager) {

        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
    }

    private Long obtenerIdUsuarioAutenticado() {

        Authentication autenticacion =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        // Permitir compras de invitados
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
    @Transactional
    public Pedido crearPedido(PedidoRequest solicitud) {

        if (solicitud == null
                || solicitud.cliente() == null
                || solicitud.items() == null
                || solicitud.items().isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El pedido debe incluir cliente y productos"
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

        Map<Long, Integer> cantidades =
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
                            "Los productos deben tener ID y cantidad validos"
                    );
                }

                cantidades.merge(
                        item.id(),
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

        long total = 0L;

        Map<Long, Producto> productos =
                new LinkedHashMap<>();

        Map<Long, Long> subtotales =
                new LinkedHashMap<>();

        List<Long> idsOrdenados = cantidades
                .keySet()
                .stream()
                .sorted()
                .toList();

        try {

            for (Long productoId : idsOrdenados) {

                Producto producto = entityManager.find(
                        Producto.class,
                        productoId,
                        LockModeType.PESSIMISTIC_WRITE
                );

                if (producto == null
                        || !Boolean.TRUE.equals(
                                producto.getActivo())) {

                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Producto no encontrado: " + productoId
                    );
                }

                int cantidad = cantidades.get(productoId);

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

                productos.put(productoId, producto);
                subtotales.put(productoId, subtotal);
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
                .toLowerCase();

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

        List<DetallePedido> detalles =
                new ArrayList<>();

        for (Map.Entry<Long, Producto> entrada
                : productos.entrySet()) {

            Long productoId = entrada.getKey();
            Producto producto = entrada.getValue();

            int cantidad = cantidades.get(productoId);

            DetallePedido detalle = new DetallePedido();

            detalle.setPedidoId(pedidoGuardado.getId());
            detalle.setProductoId(productoId);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setSubtotal(subtotales.get(productoId));

            detalles.add(detalle);

            producto.setStock(
                    producto.getStock() - cantidad
            );
        }
        detallePedidoRepository.saveAll(detalles);

        return pedidoGuardado;
    }
}
