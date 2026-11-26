
package cl.thereyesrecords.disquera.controller;

import cl.thereyesrecords.disquera.dto.PedidoRequest;
import cl.thereyesrecords.disquera.model.Pedido;
import cl.thereyesrecords.disquera.repository.PedidoRepository;
import cl.thereyesrecords.disquera.service.PedidoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webpay")
@SecurityRequirement(name = "bearerAuth")
public class WebpayController {

    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final RestClient restClient;
    private final String returnUrl;

    public WebpayController(
            PedidoService pedidoService,
            PedidoRepository pedidoRepository,
            @Value("${webpay.return-url}") String returnUrl,
            @Value("${webpay.api-key-id}") String commerceCode,
            @Value("${webpay.api-key-secret}") String apiKey) {

        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.returnUrl = returnUrl;

        // Ambiente de integracion: no procesa cobros reales.
        this.restClient = RestClient.builder()
                .baseUrl("https://webpay3gint.transbank.cl/rswebpaytransaction/api/webpay/v1.2/transactions")
                .defaultHeader("Tbk-Api-Key-Id", commerceCode)
                .defaultHeader("Tbk-Api-Key-Secret", apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciarPago(
            @Valid @RequestBody PedidoRequest solicitud) {

        Pedido pedido = pedidoService.crearPedidoWebpay(solicitud);

        try {
            Map<String, Object> body = new LinkedHashMap<>();

            body.put("buy_order", "TRR-" + pedido.getId());
            body.put("session_id", "SES-" + pedido.getId());
            body.put("amount", pedido.getTotal());
            body.put("return_url", returnUrl);

            Map<?, ?> response = restClient.post()
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response == null
                    || response.get("token") == null
                    || response.get("url") == null) {

                throw new IllegalStateException(
                        "Respuesta incompleta de Webpay");
            }

            String token = response.get("token").toString();

            pedido.setTokenWs(token);
            pedidoRepository.save(pedido);

            return ResponseEntity.ok(Map.of(
                    "pedidoId", pedido.getId(),
                    "url", response.get("url").toString(),
                    "token", token
            ));

        } catch (RuntimeException e) {

            pedidoService.finalizarWebpayFallido(
                    pedido.getId(),
                    "error_webpay"
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo iniciar la transaccion con Webpay"
            );
        }
    }

    // Bloqueo por pedido para evitar doble devolucion de stock.
    @PostMapping("/confirmar")
    @Transactional
    public ResponseEntity<?> confirmarPago(
            @RequestBody Map<String, String> payload) {

        String token = payload.get("token_ws");
        String tbkToken = payload.get("TBK_TOKEN");

        boolean cancelacion =
                (token == null || token.isBlank())
                && tbkToken != null
                && !tbkToken.isBlank();

        if (cancelacion) {
            token = tbkToken;
        }

        if (token == null || token.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Falta el token de Webpay"
            );
        }

        // Verifica propietario y bloquea el pedido.
        Pedido pedido = pedidoService.buscarPedidoWebpayBloqueado(token);

        // Evita procesar nuevamente un pago aprobado.
        if ("pagado_webpay".equals(pedido.getEstado())) {
            return respuestaAprobada(pedido);
        }

        // Evita devolver el stock mas de una vez.
        if (!"pendiente_pago".equals(pedido.getEstado())) {
            return respuestaNoAprobada(pedido);
        }

        Map<?, ?> resultado;
        boolean abortadoTransbank = false;

        try {

            resultado = cancelacion
                    ? consultarEstado(token)
                    : restClient.put()
                            .uri("/{token}", token)
                            .retrieve()
                            .body(Map.class);

        } catch (RestClientException ex) {

            // Detecta una transaccion abortada por Transbank.
            if (ex instanceof RestClientResponseException respuesta) {

                abortadoTransbank =
                        respuesta.getStatusCode().value() == 422
                        && respuesta.getResponseBodyAsString().contains(
                            "Transaction has an invalid finished state: aborted"
                        );
            }

            // Consulta el estado antes de modificar el pedido.
            try {

                resultado = consultarEstado(token);

            } catch (RestClientException fallo) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "No se pudo verificar el estado del pago"
                );
            }
        }

        // Valida que la orden y el monto coincidan.
        if (resultado == null
                || !coincidePedido(resultado, pedido)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo verificar la orden y el monto del pago"
            );
        }

        String status = String.valueOf(resultado.get("status"));

        int code = resultado.get("response_code") instanceof Number n
                ? n.intValue()
                : -1;

        // Pago aprobado por Transbank.
        if ("AUTHORIZED".equals(status) && code == 0) {

            Object codigo = resultado.get("authorization_code");

            pedido.setCodigoAutorizacion(
                    codigo == null ? "" : codigo.toString()
            );

            pedido.setEstado("pagado_webpay");

            pedidoRepository.save(pedido);

            return respuestaAprobada(pedido);
        }

        // Pago rechazado o reversado.
        if ("FAILED".equals(status)
                || "REVERSED".equals(status)) {

            pedidoService.finalizarWebpayFallido(
                    pedido.getId(),
                    cancelacion
                            ? "anulado_webpay"
                            : "rechazado_webpay"
            );

            return respuestaNoAprobada(pedido);
        }

        // Transaccion abortada confirmada por Transbank.
        if (abortadoTransbank && "INITIALIZED".equals(status)) {

            pedidoService.finalizarWebpayFallido(
                    pedido.getId(),
                    "anulado_webpay"
            );

            return respuestaNoAprobada(pedido);
        }

        // No devolver stock ante estados ambiguos.
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El pago no tiene un resultado definitivo; consulta nuevamente su estado"
        );
    }

    // Consulta el estado directamente en Transbank.
    private Map<?, ?> consultarEstado(String token) {

        return restClient.get()
                .uri("/{token}", token)
                .retrieve()
                .body(Map.class);
    }

    // Verifica que el monto y la orden sean correctos.
    private boolean coincidePedido(
            Map<?, ?> resultado,
            Pedido pedido) {

        if (!("TRR-" + pedido.getId()).equals(
                resultado.get("buy_order"))) {

            return false;
        }

        Object monto = resultado.get("amount");

        if (monto == null || pedido.getTotal() == null) {
            return false;
        }

        try {

            return new BigDecimal(monto.toString())
                    .compareTo(
                            new BigDecimal(
                                    pedido.getTotal().toString()
                            )
                    ) == 0;

        } catch (NumberFormatException ex) {

            return false;
        }
    }

    // Respuesta para un pago aprobado.
    private ResponseEntity<?> respuestaAprobada(Pedido pedido) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("aprobado", true);
        respuesta.put("idOrden", pedido.getId());
        respuesta.put("total", pedido.getTotal());
        respuesta.put("estado", pedido.getEstado());
        respuesta.put("medioPago", "Webpay Plus");
        respuesta.put(
                "codigoAutorizacion",
                pedido.getCodigoAutorizacion()
        );

        return ResponseEntity.ok(respuesta);
    }

    // Respuesta para un pago rechazado o anulado.
    private ResponseEntity<?> respuestaNoAprobada(Pedido pedido) {

        boolean anulado =
                "anulado_webpay".equals(pedido.getEstado());

        return ResponseEntity.ok(Map.of(
                "aprobado", false,
                "estado", anulado ? "anulado" : "rechazado",
                "mensaje", anulado
                        ? "El pago fue anulado"
                        : "El pago no fue aprobado"
        ));
    }

    // Impide aprobar pagos manualmente sin Transbank.
    @PostMapping("/simular-aprobacion")
    public ResponseEntity<?> simularAprobacion(
            @RequestBody Map<String, String> payload) {

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "La aprobacion manual de Webpay esta deshabilitada"
        );
    }
}
