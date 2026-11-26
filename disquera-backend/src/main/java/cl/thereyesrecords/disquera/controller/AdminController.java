package cl.thereyesrecords.disquera.controller;

import cl.thereyesrecords.disquera.model.DetallePedido;
import cl.thereyesrecords.disquera.model.DetallePlanPedido;
import cl.thereyesrecords.disquera.model.Pedido;
import cl.thereyesrecords.disquera.model.Plan;
import cl.thereyesrecords.disquera.model.Producto;
import cl.thereyesrecords.disquera.model.Usuario;
import cl.thereyesrecords.disquera.repository.DetallePedidoRepository;
import cl.thereyesrecords.disquera.repository.DetallePlanPedidoRepository;
import cl.thereyesrecords.disquera.repository.PedidoRepository;
import cl.thereyesrecords.disquera.repository.PlanRepository;
import cl.thereyesrecords.disquera.repository.ProductoRepository;
import cl.thereyesrecords.disquera.repository.UsuarioRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/admin")
@SuppressWarnings({"rawtypes", "unchecked"})
public class AdminController {

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final DetallePlanPedidoRepository detallePlanPedidoRepository;
    private final ProductoRepository productoRepository;
    private final PlanRepository planRepository;
    private final UsuarioRepository usuarioRepository;

    public AdminController(
            PedidoRepository pedidoRepository,
            DetallePedidoRepository detallePedidoRepository,
            DetallePlanPedidoRepository detallePlanPedidoRepository,
            ProductoRepository productoRepository,
            PlanRepository planRepository,
            UsuarioRepository usuarioRepository
    ) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.detallePlanPedidoRepository = detallePlanPedidoRepository;
        this.productoRepository = productoRepository;
        this.planRepository = planRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/ventas")
    public List listarVentas() {
        List<Pedido> pedidos = pedidoRepository.findAll();
        pedidos.sort((a, b) -> {
            if (a.getFechaPedido() == null || b.getFechaPedido() == null) return 0;
            return b.getFechaPedido().compareTo(a.getFechaPedido());
        });

        List resultado = new ArrayList<>();
        for (Pedido p : pedidos) {
            resultado.add(construirDetallePedido(p));
        }
        return resultado;
    }

    @GetMapping("/pedidos-pendientes")
    public List listarPedidosPendientes() {
        List<Pedido> pedidos = pedidoRepository.findAll();
        pedidos.sort((a, b) -> {
            if (a.getFechaPedido() == null || b.getFechaPedido() == null) return 0;
            return b.getFechaPedido().compareTo(a.getFechaPedido());
        });

        List resultado = new ArrayList<>();
        for (Pedido p : pedidos) {
            if ("pendiente".equalsIgnoreCase(p.getEstado())
                    || "pendiente_pago".equalsIgnoreCase(p.getEstado())) {
                resultado.add(construirDetallePedido(p));
            }
        }
        return resultado;
    }

    @GetMapping("/usuarios")
    public List listarUsuarios() {
        List resultado = new ArrayList<>();
        for (Usuario u : usuarioRepository.findAll()) {
            Map map = new LinkedHashMap();
            map.put("id", u.getId());
            map.put("nombre", u.getNombre());
            map.put("apellido", u.getApellido());
            map.put("correo", u.getCorreo());
            map.put("telefono", u.getTelefono());
            map.put("direccion", u.getDireccion());
            map.put("rol", u.getRol());
            map.put("activo", u.getActivo());
            resultado.add(map);
        }
        return resultado;
    }

    private Map construirDetallePedido(Pedido p) {
        Map dto = new LinkedHashMap();
        dto.put("id", p.getId());
        dto.put("usuarioId", p.getUsuarioId());
        dto.put("nombreCliente", p.getNombreCliente());
        dto.put("correoCliente", p.getCorreoCliente());
        dto.put("telefono", p.getTelefono());
        dto.put("direccionEnvio", p.getDireccionEnvio());
        dto.put("total", p.getTotal());
        dto.put("medioPago", p.getMedioPago());
        dto.put("estado", p.getEstado());
        dto.put("fechaPedido", p.getFechaPedido());

        List items = new ArrayList<>();

        for (DetallePedido dp : detallePedidoRepository.findAll()) {
            if (Objects.equals(dp.getPedidoId(), p.getId())) {
                String nombreProd = productoRepository.findById(dp.getProductoId())
                        .map(Producto::getNombre)
                        .orElse("Producto #" + dp.getProductoId());

                Map item = new LinkedHashMap();
                item.put("tipo", "Producto");
                item.put("nombre", nombreProd);
                item.put("cantidad", dp.getCantidad());
                item.put("precioUnitario", dp.getPrecioUnitario());
                item.put("subtotal", dp.getSubtotal());
                items.add(item);
            }
        }

        for (DetallePlanPedido dpp : detallePlanPedidoRepository.findAll()) {
            if (Objects.equals(dpp.getPedidoId(), p.getId())) {
                String nombrePlan = planRepository.findById(dpp.getPlanId())
                        .map(Plan::getNombre)
                        .orElse("Plan #" + dpp.getPlanId());

                Map item = new LinkedHashMap();
                item.put("tipo", "Plan");
                item.put("nombre", nombrePlan);
                item.put("cantidad", dpp.getCantidad());
                item.put("precioUnitario", dpp.getPrecioUnitario());
                item.put("subtotal", dpp.getSubtotal());
                items.add(item);
            }
        }

        dto.put("items", items);
        return dto;
    }
}