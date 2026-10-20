package com.taqueria.service;

import com.taqueria.dto.Dtos.RecetaDetalleRequest;
import com.taqueria.dto.Dtos.RecetaRequest;
import com.taqueria.model.*;
import com.taqueria.repository.*;
import com.taqueria.security.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Recetas: que articulos de inventario consume cada producto. Cambiar una receta no
 * modifica ventas anteriores, porque cada venta guarda sus propios movimientos.
 */
@Service
public class RecetaService {

    private final RecetaRepository recetas;
    private final ProductoRepository productos;
    private final InventarioItemRepository items;
    private final UnidadMedidaRepository unidades;
    private final UnidadService conversion;
    private final AuditoriaService auditoria;

    public RecetaService(RecetaRepository recetas, ProductoRepository productos, InventarioItemRepository items,
                         UnidadMedidaRepository unidades, UnidadService conversion, AuditoriaService auditoria) {
        this.recetas = recetas;
        this.productos = productos;
        this.items = items;
        this.unidades = unidades;
        this.conversion = conversion;
        this.auditoria = auditoria;
    }

    public Optional<Receta> obtener(Long productoId) {
        return recetas.findByProductoId(productoId);
    }

    /** Costo de la receta con el costo actual de cada articulo. */
    public BigDecimal costo(Receta r) {
        BigDecimal total = BigDecimal.ZERO;
        for (RecetaDetalle d : r.getDetalles()) {
            BigDecimal cant = conversion.convertir(d.getCantidad(), d.getUnidad(), d.getItem().getUnidad());
            total = total.add(cant.multiply(d.getItem().getCostoUnitario()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Estado de recetas de todos los productos: si tienen receta y su costo. */
    public List<Map<String, Object>> resumen() {
        Map<Long, Receta> porProducto = new HashMap<>();
        recetas.findAll().forEach(r -> porProducto.put(r.getProducto().getId(), r));
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Producto p : productos.findAllByOrderByCategoriaOrdenAscNombreAsc()) {
            Receta r = porProducto.get(p.getId());
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("productoId", p.getId());
            fila.put("codigo", p.getCodigo());
            fila.put("nombre", p.getNombre());
            fila.put("categoria", p.getCategoria().getNombre());
            fila.put("tipo", p.getTipo());
            fila.put("activo", p.isActivo());
            fila.put("precio", p.getPrecio());
            fila.put("costoEstimado", p.getCostoEstimado());
            boolean reventa = p.getTipo() == TipoProducto.REVENTA && p.getItemInventario() != null;
            fila.put("descuentoAutomatico", reventa ? "REVENTA" : (r != null && !r.getDetalles().isEmpty() ? "RECETA" : "SIN_CONFIGURAR"));
            fila.put("ingredientes", r == null ? 0 : r.getDetalles().size());
            fila.put("costoReceta", r == null ? null : costo(r));
            lista.add(fila);
        }
        return lista;
    }

    @Transactional
    public Receta guardar(Long productoId, RecetaRequest req) {
        Producto p = productos.findById(productoId).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        if (p.getTipo() == TipoProducto.REVENTA) {
            throw new ApiException("Los productos de reventa descuentan su artículo de inventario y no usan receta");
        }
        Set<Long> vistos = new HashSet<>();
        for (RecetaDetalleRequest d : req.detalles()) {
            if (!vistos.add(d.itemId())) throw new ApiException("Un ingrediente aparece dos veces en la receta");
        }
        Receta r = recetas.findByProductoId(productoId).orElseGet(() -> {
            Receta nueva = new Receta();
            nueva.setProducto(p);
            return nueva;
        });
        r.getDetalles().clear();
        recetas.saveAndFlush(r);
        for (RecetaDetalleRequest d : req.detalles()) {
            InventarioItem it = items.findById(d.itemId()).orElseThrow(() -> ApiException.noEncontrado("Ingrediente"));
            UnidadMedida u = unidades.findById(d.unidadId()).orElseThrow(() -> ApiException.noEncontrado("Unidad"));
            if (!u.esCompatibleCon(it.getUnidad())) {
                throw new ApiException("La unidad " + u.getNombre() + " no es compatible con " + it.getNombre()
                        + " (se controla en " + it.getUnidad().getNombre() + ")");
            }
            RecetaDetalle det = new RecetaDetalle();
            det.setReceta(r);
            det.setItem(it);
            det.setCantidad(d.cantidad());
            det.setUnidad(u);
            r.getDetalles().add(det);
        }
        r.setNotas(Texto.limpio(req.notas()));
        r.setActualizadoPor(UsuarioActual.obtener().getUsername());
        r.setActualizadoEn(Instant.now());
        recetas.save(r);
        auditoria.registrar("Receta actualizada", "Producto", p.getId(),
                p.getNombre() + ": " + r.getDetalles().size() + " ingrediente(s), costo $" + costo(r));
        return r;
    }

    @Transactional
    public void eliminar(Long productoId) {
        recetas.findByProductoId(productoId).ifPresent(r -> {
            recetas.delete(r);
            auditoria.registrar("Receta eliminada", "Producto", productoId, r.getProducto().getNombre());
        });
    }
}
