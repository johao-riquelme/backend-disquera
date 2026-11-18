package cl.thereyesrecords.disquera.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenGoogleRequest(
    @NotBlank(message = "El token de Google es obligatorio")
    String token
) {}