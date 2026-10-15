
package cl.thereyesrecords.disquera.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    public ResponseEntity<Map<String, String>> obtenerToken(
            CsrfToken csrfToken) {
        Map<String, String> respuesta =
                new LinkedHashMap<>();
        respuesta.put(
                "headerName",
                csrfToken.getHeaderName()
        );
        respuesta.put(
                "token",
                csrfToken.getToken()
        );
        return ResponseEntity.ok(respuesta);
    }
}
