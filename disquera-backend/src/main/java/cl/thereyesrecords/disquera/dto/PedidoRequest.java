
package cl.thereyesrecords.disquera.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PedidoRequest(

    @NotNull(message = "Los datos del cliente son obligatorios")
    @Valid
    Cliente cliente,

    @NotEmpty(message = "El carrito no puede estar vacio")
    @Valid
    List<Item> items,

    // El total enviado por React no se utiliza para cobrar.
    // Spring Boot lo calculara desde Neon.
    Long total

) {

    public record Cliente(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100)
        String nombre,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100)
        String apellido,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo no valido")
        String email,

        @NotBlank(message = "La direccion es obligatoria")
        String direccion,

        @NotBlank(message = "La comuna es obligatoria")
        String comuna,

        @NotBlank(message = "La region es obligatoria")
        String region,

        String metodoPago

    ) {
    }

    public record Item(

        @NotNull(message = "El ID del producto es obligatorio")
        @Positive(message = "El ID debe ser positivo")
        Long id,

        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        Integer cantidad

    ) {
    }
}
