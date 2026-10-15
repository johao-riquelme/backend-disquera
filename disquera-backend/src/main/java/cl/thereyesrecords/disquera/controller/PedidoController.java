package cl.thereyesrecords.disquera.controller;

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
import cl.thereyesrecords.disquera.repository.ProductoRepository;
import cl.thereyesrecords.disquera.repository.PlanRepository;
import cl.thereyesrecords.disquera.repository.UsuarioRepository;

import cl.thereyesrecords.disquera.service.PedidoService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final DetallePlanPedidoRepository detallePlanPedidoRepository;
    private final ProductoRepository productoRepository;
    private final PlanRepository planRepository;
    private final UsuarioRepository usuarioRepository;


    public PedidoController(
            PedidoService pedidoService,
            PedidoRepository pedidoRepository,
            DetallePedidoRepository detallePedidoRepository,
            DetallePlanPedidoRepository detallePlanPedidoRepository,
            ProductoRepository productoRepository,
            PlanRepository planRepository,
            UsuarioRepository usuarioRepository) {

        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.detallePlanPedidoRepository =
                detallePlanPedidoRepository;
        this.productoRepository = productoRepository;
        this.planRepository = planRepository;
        this.usuarioRepository = usuarioRepository;
    }


    @PostMapping
    public ResponseEntity<Map<String, Object>> crearPedido(
            @Valid @RequestBody PedidoRequest solicitud) {

        Pedido pedido = pedidoService.crearPedido(solicitud);

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("id", pedido.getId());
        respuesta.put("numeroPedido", pedido.getId());
        respuesta.put("total", pedido.getTotal());
        respuesta.put("estado", pedido.getEstado());
        respuesta.put("medioPago", pedido.getMedioPago());

        respuesta.put(
                "mensaje",
                "Compra simulada realizada correctamente"
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }

    @GetMapping("/mis-compras")
    public ResponseEntity<List<Map<String, Object>>> misCompras(
            Authentication autenticacion) {


        if (autenticacion == null
                || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(
                        autenticacion.getPrincipal())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Debes iniciar sesion para ver tus compras"
            );
        }


        String correo = autenticacion.getName();

        Usuario usuario = usuarioRepository
                .findByCorreoIgnoreCase(correo)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "La sesion no corresponde a un usuario valido"
                        )
                );

        if (!Boolean.TRUE.equals(usuario.getActivo())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El usuario se encuentra desactivado"
            );
        }

        List<Pedido> pedidos = new ArrayList<>(
                pedidoRepository.findByUsuarioId(usuario.getId())
        );

        pedidos.sort(
                Comparator.comparing(Pedido::getId).reversed()
        );

        List<Map<String, Object>> historial =
                new ArrayList<>();

        for (Pedido pedido : pedidos) {

            Map<String, Object> compra =
                    new LinkedHashMap<>();

            compra.put("id", pedido.getId());
            compra.put("numeroPedido", pedido.getId());
            compra.put("fecha", pedido.getFechaPedido());
            compra.put("total", pedido.getTotal());
            compra.put("estado", pedido.getEstado());
            compra.put("medioPago", pedido.getMedioPago());

            compra.put(
                    "direccionEnvio",
                    pedido.getDireccionEnvio()
            );

            List<DetallePedido> detallesProductos =
                    detallePedidoRepository
                            .findByPedidoId(pedido.getId());

            List<Map<String, Object>> productosComprados =
                    new ArrayList<>();

            for (DetallePedido detalle : detallesProductos) {

                String nombreProducto = productoRepository
                        .findById(detalle.getProductoId())
                        .map(Producto::getNombre)
                        .orElse("Producto no disponible");

                Map<String, Object> producto =
                        new LinkedHashMap<>();

                producto.put(
                        "productoId",
                        detalle.getProductoId()
                );
                producto.put(
                        "nombre",
                        nombreProducto
                );
                producto.put(
                        "cantidad",
                        detalle.getCantidad()
                );
                producto.put(
                        "precioUnitario",
                        detalle.getPrecioUnitario()
                );
                producto.put(
                        "subtotal",
                        detalle.getSubtotal()
                );
                productosComprados.add(producto);
            }

            compra.put("productos", productosComprados);

            List<DetallePlanPedido> detallesPlanes =
                    detallePlanPedidoRepository
                            .findByPedidoId(pedido.getId());

            List<Map<String, Object>> planesContratados =
                    new ArrayList<>();

            for (DetallePlanPedido detalle : detallesPlanes) {

                String nombrePlan = planRepository
                        .findById(detalle.getPlanId())
                        .map(Plan::getNombre)
                        .orElse("Plan no disponible");

                Map<String, Object> plan =
                        new LinkedHashMap<>();

                plan.put(
                        "planId",
                        detalle.getPlanId()
                );

                plan.put(
                        "nombre",
                        nombrePlan
                );

                plan.put(
                        "cantidad",
                        detalle.getCantidad()
                );

                plan.put(
                        "precioUnitario",
                        detalle.getPrecioUnitario()
                );

                plan.put(
                        "subtotal",
                        detalle.getSubtotal()
                );

                planesContratados.add(plan);
            }
            compra.put("planes", planesContratados);

            historial.add(compra);
        }
        return ResponseEntity.ok(historial);
    }
}
