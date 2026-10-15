package cl.thereyesrecords.disquera.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        @NotNull(message = "El ID del articulo es obligatorio")
        @Positive(message = "El ID debe ser positivo")
        Long id,
        @NotNull(message = "La cantidad es obligatoria")
        @Positive(message = "La cantidad debe ser mayor a cero")
        Integer cantidad,
        @Pattern(
            regexp = "(?i)(producto|plan)",
            message = "El tipo debe ser producto o plan"
        )
        String tipo
    ) {
        public Item(Long id, Integer cantidad) {
            this(id, cantidad, null);
        }

    }
}
