package cl.thereyesrecords.disquera.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import cl.thereyesrecords.disquera.model.Producto;
import cl.thereyesrecords.disquera.repository.ProductoRepository;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }
    public List<Producto> listar() {
        return productoRepository.findByActivoTrue();
    }
    public Producto buscarPorId(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Producto no encontrado"
                ));
    }
    public Producto guardar(Producto producto) {
        validarProducto(producto);
        producto.setId(null);
        producto.setActivo(true);
        producto.setFechaCreacion(null);

        return productoRepository.save(producto);
    }

    public Producto actualizar(Long id, Producto datos) {
        Producto producto = buscarPorId(id);
        validarProducto(datos);

        producto.setNombre(datos.getNombre());
        producto.setDescripcion(datos.getDescripcion());
        producto.setCategoria(datos.getCategoria());
        producto.setPrecio(datos.getPrecio());
        producto.setStock(datos.getStock());
        producto.setImagen(datos.getImagen());

        return productoRepository.save(producto);
    }
    public void desactivar(Long id) {
        Producto producto = buscarPorId(id);
        producto.setActivo(false);

        productoRepository.save(producto);
    }
    private void validarProducto(Producto producto) {
        if (producto.getNombre() == null ||
                producto.getNombre().isBlank() ||
                producto.getNombre().length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Nombre no válido"
            );
        }
        if (producto.getCategoria() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Categoría obligatoria"
            );
        }
        String categoria = producto.getCategoria()
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!categoria.equals("microfono") &&
                !categoria.equals("interfaz")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Categoría no válida"
            );
        }
        if (producto.getPrecio() == null ||
                producto.getPrecio() < 0 ||
                producto.getStock() == null ||
                producto.getStock() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Precio o stock no válido"
            );
        }
        producto.setNombre(producto.getNombre().trim());
        producto.setCategoria(categoria);
    }
}
