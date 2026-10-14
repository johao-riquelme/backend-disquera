
package cl.thereyesrecords.disquera.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "nombre_cliente", nullable = false, length = 150)
    private String nombreCliente;

    @Column(name = "correo_cliente", nullable = false, length = 255)
    private String correoCliente;

    @Column(length = 20)
    private String telefono;

    @Column(name = "direccion_envio", nullable = false)
    private String direccionEnvio;

    @Column(nullable = false)
    private Long total = 0L;

    @Column(name = "medio_pago", nullable = false, length = 20)
    private String medioPago = "simulado";

    @Column(nullable = false, length = 25)
    private String estado = "pendiente";

    @Column(name = "fecha_pedido")
    private OffsetDateTime fechaPedido;

    public Pedido() {
    }

    @PrePersist
    public void antesDeGuardar() {
        if (fechaPedido == null) {
            fechaPedido = OffsetDateTime.now();
        }

        if (medioPago == null) {
            medioPago = "simulado";
        }

        if (estado == null) {
            estado = "pendiente";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getCorreoCliente() {
        return correoCliente;
    }

    public void setCorreoCliente(String correoCliente) {
        this.correoCliente = correoCliente;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDireccionEnvio() {
        return direccionEnvio;
    }

    public void setDireccionEnvio(String direccionEnvio) {
        this.direccionEnvio = direccionEnvio;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public String getMedioPago() {
        return medioPago;
    }

    public void setMedioPago(String medioPago) {
        this.medioPago = medioPago;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public OffsetDateTime getFechaPedido() {
        return fechaPedido;
    }

    public void setFechaPedido(OffsetDateTime fechaPedido) {
        this.fechaPedido = fechaPedido;
    }
}
