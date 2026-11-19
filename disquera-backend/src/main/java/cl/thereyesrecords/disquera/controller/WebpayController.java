package cl.thereyesrecords.disquera.controller;

import cl.thereyesrecords.disquera.dto.PedidoRequest;
import cl.thereyesrecords.disquera.model.Pedido;
import cl.thereyesrecords.disquera.repository.PedidoRepository;
import cl.thereyesrecords.disquera.service.PedidoService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webpay")
public class WebpayController {

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final RestClient restClient;

    @Value("${webpay.return-url}")
    private String returnUrl;

    public WebpayController(PedidoService pedidoService, PedidoRepository pedidoRepository) {
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.restClient = RestClient.builder()
                .baseUrl("https://webpay3gint.transbank.cl/rswebpaytransaction/api/webpay/v1.2/transactions")
                .defaultHeader("Tbk-Api-Key-Id", "597055555532")
                .defaultHeader("Tbk-Api-Key-Secret", "579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @RequestMapping(path = "/iniciar", method = RequestMethod.POST)
    public ResponseEntity<?> iniciarPago(@Valid @RequestBody PedidoRequest solicitud) {
        Pedido pedido = pedidoService.crearPedido(solicitud);

        if (pedido.getTotal() == null || pedido.getTotal() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto total debe ser mayor a cero");
        }

        try {
            Map<String, Object> bodyTransbank = new LinkedHashMap<>();
            bodyTransbank.put("buy_order", "TRR-" + pedido.getId());
            bodyTransbank.put("session_id", "SES-" + pedido.getId() + "-" + System.currentTimeMillis());
            bodyTransbank.put("amount", pedido.getTotal());
            bodyTransbank.put("return_url", returnUrl);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .body(bodyTransbank)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("token") == null || response.get("url") == null) {
                throw new RuntimeException("Respuesta invalida desde Transbank");
            }

            String token = response.get("token").toString();
            String url = response.get("url").toString();

            pedido.setTokenWs(token);
            pedidoRepository.save(pedido);

            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("pedidoId", pedido.getId());
            resultado.put("url", url);
            resultado.put("token", token);

            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            pedido.setEstado("error_webpay");
            pedidoRepository.save(pedido);
            pedidoService.revertirStockPedido(pedido.getId());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al iniciar transaccion con Transbank: " + e.getMessage()
            );
        }
    }

    @RequestMapping(path = "/confirmar", method = RequestMethod.POST)
    public ResponseEntity<?> confirmarPago(@RequestBody Map<String, String> payload) {
        String tokenWs = payload.get("token_ws");
        String tbkToken = payload.get("TBK_TOKEN");

        if ((tokenWs == null || tokenWs.isBlank()) && tbkToken != null && !tbkToken.isBlank()) {
            pedidoRepository.findByTokenWs(tbkToken).ifPresent(pedido -> {
                if ("pendiente_pago".equals(pedido.getEstado())) {
                    pedido.setEstado("anulado_webpay");
                    pedidoRepository.save(pedido);
                    pedidoService.revertirStockPedido(pedido.getId());
                }
            });

            Map<String, Object> anulado = new LinkedHashMap<>();
            anulado.put("aprobado", false);
            anulado.put("estado", "anulado");
            anulado.put("mensaje", "El pago fue anulado por el usuario en Webpay.");
            return ResponseEntity.ok(anulado);
        }

        if (tokenWs == null || tokenWs.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el parametro token_ws");
        }

        Pedido pedido = pedidoRepository.findByTokenWs(tokenWs)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado para el token"));

        if ("pagado_webpay".equals(pedido.getEstado())) {
            Map<String, Object> yaPagado = new LinkedHashMap<>();
            yaPagado.put("aprobado", true);
            yaPagado.put("idOrden", pedido.getId());
            yaPagado.put("total", pedido.getTotal());
            yaPagado.put("estado", pedido.getEstado());
            yaPagado.put("medioPago", "Webpay Plus");
            yaPagado.put("codigoAutorizacion", pedido.getCodigoAutorizacion());
            return ResponseEntity.ok(yaPagado);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> commitResponse = restClient.put()
                    .uri("/{token}", tokenWs)
                    .retrieve()
                    .body(Map.class);

            String status = commitResponse != null && commitResponse.get("status") != null
                    ? commitResponse.get("status").toString()
                    : "";
            int responseCode = commitResponse != null && commitResponse.get("response_code") instanceof Number num
                    ? num.intValue()
                    : -1;

            boolean esAprobado = "AUTHORIZED".equalsIgnoreCase(status) && responseCode == 0;

            if (esAprobado) {
                String authCode = commitResponse.get("authorization_code") != null
                        ? commitResponse.get("authorization_code").toString()
                        : "";

                pedido.setEstado("pagado_webpay");
                pedido.setCodigoAutorizacion(authCode);
                pedidoRepository.save(pedido);

                Map<String, Object> aprobado = new LinkedHashMap<>();
                aprobado.put("aprobado", true);
                aprobado.put("idOrden", pedido.getId());
                aprobado.put("total", pedido.getTotal());
                aprobado.put("estado", pedido.getEstado());
                aprobado.put("medioPago", "Webpay Plus");
                aprobado.put("codigoAutorizacion", authCode);
                return ResponseEntity.ok(aprobado);
            } else {
                pedido.setEstado("rechazado_webpay");
                pedidoRepository.save(pedido);
                pedidoService.revertirStockPedido(pedido.getId());

                Map<String, Object> rechazado = new LinkedHashMap<>();
                rechazado.put("aprobado", false);
                rechazado.put("estado", "rechazado");
                rechazado.put("mensaje", "La transaccion fue rechazada por el banco o emisor.");
                return ResponseEntity.ok(rechazado);
            }

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al confirmar transaccion con Transbank: " + e.getMessage()
            );
        }
    }

    @RequestMapping(path = "/simular-aprobacion", method = RequestMethod.POST)
    public ResponseEntity<?> simularAprobacion(@RequestBody Map<String, String> payload) {
        String tokenWs = payload.get("token_ws");
        if (tokenWs == null || tokenWs.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el parametro token_ws");
        }

        Pedido pedido = pedidoRepository.findByTokenWs(tokenWs)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado para el token"));

        String authCode = String.valueOf((int) (Math.random() * 900000) + 100000);
        pedido.setEstado("pagado_webpay");
        pedido.setCodigoAutorizacion(authCode);
        pedidoRepository.save(pedido);

        Map<String, Object> aprobado = new LinkedHashMap<>();
        aprobado.put("aprobado", true);
        aprobado.put("idOrden", pedido.getId());
        aprobado.put("total", pedido.getTotal());
        aprobado.put("estado", "pagado_webpay");
        aprobado.put("medioPago", "Webpay Plus (Simulado)");
        aprobado.put("codigoAutorizacion", authCode);
        return ResponseEntity.ok(aprobado);
    }
}