
package cl.thereyesrecords.disquera.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class PedidoErrorHandler {

    // ==========================================
    // ERRORES DE VALIDACION DEL FORMULARIO
    // ==========================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> errorValidacion(
            MethodArgumentNotValidException ex) {

        String detalle = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": "
                        + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        if (detalle.isBlank()) {
            detalle = "Datos del pedido no validos";
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", 400);
        respuesta.put("mensaje", "Error de validacion");
        respuesta.put("detalle", detalle);

        System.err.println(
                "ERROR DE VALIDACION: " + detalle
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    // ==========================================
    // ERRORES AL LEER EL JSON
    // ==========================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> errorJson(
            HttpMessageNotReadableException ex) {

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", 400);
        respuesta.put("mensaje", "No se pudo leer el JSON");
        respuesta.put("detalle", ex.getMessage());

        System.err.println(
                "ERROR AL LEER JSON: " + ex.getMessage()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(respuesta);
    }

    // ==========================================
    // ERRORES DEL SERVICIO DE COMPRAS
    // ==========================================

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> errorServicio(
            ResponseStatusException ex) {

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("status", ex.getStatusCode().value());
        respuesta.put("mensaje", ex.getReason());

        System.err.println(
                "ERROR DEL SERVICIO: " + ex.getReason()
        );

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(respuesta);
    }
}
