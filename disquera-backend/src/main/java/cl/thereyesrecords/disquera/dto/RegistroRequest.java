package cl.thereyesrecords.disquera.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    String nombre,

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100)
    String apellido,

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 255)
    String correo,

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "Mínimo 8 caracteres")
    String contrasena,

    @Size(max = 20)
    String telefono,

    String direccion

) {}
