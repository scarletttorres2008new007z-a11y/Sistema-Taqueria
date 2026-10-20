package com.taqueria.service;

import com.taqueria.dto.Dtos.ItemInventarioRequest;
import com.taqueria.dto.Dtos.MovimientoInventarioRequest;
import com.taqueria.model.*;
import com.taqueria.repository.*;
import com.taqueria.security.Permisos;
import com.taqueria.security.UsuarioActual;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Unico lugar donde cambia la existencia de un articulo. Cada cambio queda registrado
 * como movimiento con usuario, motivo y referencia, y se hace con la fila bloqueada para
 * que operaciones simultaneas no dejen cantidades incorrectas.
 */
@Service
public class InventarioService {

    public static final String REF_VENTA = "VENTA";
    public static final String REF_COMPRA = "COMPRA";

    private final InventarioItemRepository items;
    private final MovimientoInventarioRepository movimientos;
    private final UnidadMedidaRepository unidades;
    private final ProveedorRepository proveedores;
    private final RecetaRepository recetas;
    private final ProductoRepository productos;
    private final UnidadService conversion;
    private final AuditoriaService auditoria;

    public InventarioService(InventarioItemRepository items, MovimientoInventarioRepository movimientos,
                             UnidadMedidaRepository unidades, ProveedorRepository proveedores, RecetaRepository recetas,
                             ProductoRepository productos, UnidadService conversion, AuditoriaService auditoria) {
        this.items = items;
        this.movimientos = movimientos;
        this.unidades = unidades;
        this.proveedores = proveedores;
        this.recetas = recetas;
        this.productos = productos;
        this.conversion = conversion;
        this.auditoria = auditoria;
    }

    public List<InventarioItem> listar() {
        return items.findAllByOrderByNombreAsc();
    }

    public InventarioItem item(Long id) {
        return items.findById(id).orElseThrow(() -> ApiException.noEncontrado("Artículo de inventario"));
    }

    @Transactional
    public InventarioItem guardarItem(Long id, ItemInventarioRequest req) {
        boolean nuevo = id == null;
        InventarioItem it = nuevo ? new InventarioItem() : item(id);
        String codigo = Texto.limpio(req.codigo());
        if (codigo == null) codigo = nuevo ? String.format("IN-%04d", items.maxId() + 1) : it.getCodigo();
        codigo = codigo.toUpperCase();
        if (!codigo.equals(it.getCodigo()) && items.existsByCodigoIgnoreCase(codigo)) {
            throw ApiException.conflicto("Ya existe un artículo con el código " + codigo);
        }
        UnidadMedida unidad = unidades.findById(req.unidadId()).orElseThrow(() -> ApiException.noEncontrado("Unidad"));
        if (!nuevo && !it.getUnidad().getId().equals(unidad.getId()) && it.getExistencia().signum() != 0) {
            throw new ApiException("No se puede cambiar la unidad de un artículo con existencias. Ajuste la existencia a cero primero.");
        }
        it.setCodigo(codigo);
        it.setNombre(req.nombre().trim());
        it.setTipo(req.tipo());
        it.setCategoria(req.categoria().trim());
        it.setUnidad(unidad);
        it.setExistenciaMinima(req.existenciaMinima());
        it.setCostoUnitario(req.costoUnitario());
        it.setProveedor(req.proveedorId() == null ? null
                : proveedores.findById(req.proveedorId()).orElseThrow(() -> ApiException.noEncontrado("Proveedor")));
        it.setFechaVencimiento(req.fechaVencimiento());
        it.setLote(Texto.limpio(req.lote()));
        it.setActivo(req.activo());
        items.saveAndFlush(it);
        auditoria.registrar(nuevo ? "Artículo de inventario creado" : "Artículo de inventario modificado",
                "InventarioItem", it.getId(), it.getCodigo() + " " + it.getNombre());
        if (nuevo && req.existenciaInicial() != null && req.existenciaInicial().signum() > 0) {
            aplicar(it.getId(), req.existenciaInicial(), TipoMovimientoInventario.ENTRADA, "Existencia inicial",
                    null, null, req.costoUnitario());
        }
        return item(it.getId());
    }

    /** Entrada, salida o ajuste registrado desde la pantalla de inventario. */
    @Transactional
    public MovimientoInventario movimientoManual(MovimientoInventarioRequest req) {
        String permiso = switch (req.tipo()) {
            case ENTRADA -> Permisos.INVENTARIO_ENTRADAS;
            case SALIDA -> Permisos.INVENTARIO_SALIDAS;
            case AJUSTE -> Permisos.INVENTARIO_AJUSTAR;
            default -> throw new ApiException("Ese tipo de movimiento lo genera el sistema automáticamente");
        };
        if (!UsuarioActual.tiene(permiso)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No tiene permiso para registrar este movimiento");
        }
        BigDecimal delta;
        InventarioItem actual = item(req.itemId());
        switch (req.tipo()) {
            case ENTRADA -> {
                if (req.cantidad().signum() <= 0) throw new ApiException("La cantidad debe ser mayor que cero");
                delta = req.cantidad();
            }
            case SALIDA -> {
                if (req.cantidad().signum() <= 0) throw new ApiException("La cantidad debe ser mayor que cero");
                delta = req.cantidad().negate();
            }
            default -> {
                // Ajuste por conteo fisico: la cantidad indicada es la nueva existencia.
                delta = req.cantidad().subtract(actual.getExistencia());
                if (delta.signum() == 0) throw new ApiException("La existencia contada es igual a la registrada");
            }
        }
        MovimientoInventario m = aplicar(req.itemId(), delta, req.tipo(), req.motivo().trim(), null, null,
                req.tipo() == TipoMovimientoInventario.ENTRADA ? req.costoUnitario() : null);
        if (req.tipo() == TipoMovimientoInventario.ENTRADA && (req.lote() != null || req.fechaVencimiento() != null)) {
            InventarioItem it = item(req.itemId());
            if (!Texto.vacio(req.lote())) it.setLote(req.lote().trim());
            if (req.fechaVencimiento() != null) it.setFechaVencimiento(req.fechaVencimiento());
            items.save(it);
        }
        auditoria.registrar("Movimiento de inventario: " + req.tipo(), "InventarioItem", req.itemId(),
                actual.getNombre() + ": " + delta.stripTrailingZeros().toPlainString() + " " + actual.getUnidad().getCodigo()
                        + " (" + req.motivo().trim() + ")");
        return m;
    }

    /**
     * Cambia la existencia de un articulo y registra el movimiento.
     *
     * @param delta positivo para sumar, negativo para restar (en la unidad del articulo)
     */
    @Transactional
    public MovimientoInventario aplicar(Long itemId, BigDecimal delta, TipoMovimientoInventario tipo, String motivo,
                                        String referenciaTipo, Long referenciaId, BigDecimal costoUnitario) {
        InventarioItem it = items.bloquear(itemId).orElseThrow(() -> ApiException.noEncontrado("Artículo de inventario"));
        BigDecimal cantidad = delta.setScale(3, RoundingMode.HALF_UP);
        BigDecimal anterior = it.getExistencia();
        BigDecimal nueva = anterior.add(cantidad);
        if (nueva.signum() < 0) {
            throw ApiException.conflicto("Existencia insuficiente de " + it.getNombre() + ": hay "
                    + formato(anterior) + " " + it.getUnidad().getCodigo() + " y se necesitan "
                    + formato(cantidad.negate()) + ". Registre una entrada o un ajuste de inventario.");
        }
        it.setExistencia(nueva);
        if (costoUnitario != null && costoUnitario.signum() > 0
                && (tipo == TipoMovimientoInventario.ENTRADA || tipo == TipoMovimientoInventario.COMPRA)) {
            it.setCostoUnitario(costoUnitario);
        }
        items.save(it);

        MovimientoInventario m = new MovimientoInventario();
        m.setItem(it);
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setExistenciaAnterior(anterior);
        m.setExistenciaNueva(nueva);
        m.setCostoUnitario(costoUnitario != null ? costoUnitario : it.getCostoUnitario());
        m.setMotivo(motivo);
        m.setReferenciaTipo(referenciaTipo);
        m.setReferenciaId(referenciaId);
        m.setUsuarioId(UsuarioActual.id());
        try {
            return movimientos.saveAndFlush(m);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("Este movimiento de inventario ya fue registrado");
        }
    }

    // ---------- Consumo por ventas ----------

    /** Linea vendida: producto y cantidad. */
    public record LineaConsumo(Long productoId, int cantidad) {
    }

    /** Resultado del calculo de consumo: cantidades por articulo y productos sin receta. */
    public record Consumo(Map<Long, BigDecimal> porItem, List<String> productosSinReceta) {
    }

    /** Calcula que articulos de inventario consume una lista de productos vendidos. */
    public Consumo calcularConsumo(List<LineaConsumo> lineas) {
        Map<Long, BigDecimal> porItem = new TreeMap<>();
        Set<String> sinReceta = new LinkedHashSet<>();
        Map<Long, Integer> porProducto = new LinkedHashMap<>();
        lineas.forEach(l -> porProducto.merge(l.productoId(), l.cantidad(), Integer::sum));
        Map<Long, Receta> recetasPorProducto = new HashMap<>();
        recetas.findByProductoIdIn(porProducto.keySet()).forEach(r -> recetasPorProducto.put(r.getProducto().getId(), r));
        porProducto.forEach((productoId, cantidad) -> {
            Producto p = productos.findById(productoId).orElseThrow(() -> ApiException.noEncontrado("Producto"));
            BigDecimal cant = BigDecimal.valueOf(cantidad);
            if (p.getTipo() == TipoProducto.REVENTA && p.getItemInventario() != null) {
                porItem.merge(p.getItemInventario().getId(), cant, BigDecimal::add);
                return;
            }
            Receta r = recetasPorProducto.get(productoId);
            if (r == null || r.getDetalles().isEmpty()) {
                sinReceta.add(p.getNombre());
                return;
            }
            for (RecetaDetalle d : r.getDetalles()) {
                BigDecimal enUnidadDelItem = conversion.convertir(d.getCantidad(), d.getUnidad(), d.getItem().getUnidad());
                porItem.merge(d.getItem().getId(), enUnidadDelItem.multiply(cant), BigDecimal::add);
            }
        });
        porItem.replaceAll((k, v) -> v.setScale(3, RoundingMode.HALF_UP));
        return new Consumo(porItem, new ArrayList<>(sinReceta));
    }

    /** Verifica que haya existencias suficientes antes de registrar un pedido o una venta. */
    public void verificarDisponibilidad(List<LineaConsumo> lineas) {
        List<String> faltantes = new ArrayList<>();
        calcularConsumo(lineas).porItem().forEach((itemId, necesario) -> {
            InventarioItem it = item(itemId);
            if (it.getExistencia().compareTo(necesario) < 0) {
                faltantes.add(it.getNombre() + " (hay " + formato(it.getExistencia()) + " " + it.getUnidad().getCodigo()
                        + ", se necesitan " + formato(necesario) + ")");
            }
        });
        if (!faltantes.isEmpty()) {
            throw ApiException.conflicto("No hay existencias suficientes: " + String.join("; ", faltantes));
        }
    }

    /** Descuenta los ingredientes de una venta. El indice unico evita descontar dos veces. */
    @Transactional
    public List<String> descontarVenta(Venta venta) {
        List<LineaConsumo> lineas = venta.getDetalles().stream()
                .map(d -> new LineaConsumo(d.getProductoId(), d.getCantidad())).toList();
        Consumo consumo = calcularConsumo(lineas);
        // Orden por id de articulo para evitar bloqueos cruzados entre ventas simultaneas
        consumo.porItem().forEach((itemId, cantidad) -> aplicar(itemId, cantidad.negate(), TipoMovimientoInventario.VENTA,
                "Venta " + venta.getNumero(), REF_VENTA, venta.getId(), null));
        return consumo.productosSinReceta();
    }

    /** Devuelve exactamente lo que se desconto en la venta (devolucion autorizada). */
    @Transactional
    public void devolverVenta(Venta venta, String motivo) {
        List<MovimientoInventario> descontados = movimientos.findByReferenciaTipoAndReferenciaId(REF_VENTA, venta.getId());
        descontados.stream()
                .filter(m -> m.getTipo() == TipoMovimientoInventario.VENTA)
                .sorted(Comparator.comparing(m -> m.getItem().getId()))
                .forEach(m -> aplicar(m.getItem().getId(), m.getCantidad().negate(), TipoMovimientoInventario.DEVOLUCION_VENTA,
                        "Devolución de venta " + venta.getNumero() + ": " + motivo, REF_VENTA, venta.getId(), null));
    }

    // ---------- Consultas ----------

    public Page<MovimientoInventario> buscarMovimientos(Long itemId, TipoMovimientoInventario tipo, LocalDate desde,
                                                        LocalDate hasta, int pagina, int tamano) {
        LocalDate d = Fechas.o(desde, Fechas.hoy().minusDays(30));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        return movimientos.buscar(itemId, tipo, Fechas.inicio(d), Fechas.finExclusivo(h),
                PageRequest.of(Math.max(0, pagina), Math.min(Math.max(tamano, 1), 200)));
    }

    public Map<String, List<InventarioItem>> alertas() {
        List<InventarioItem> activos = items.findAllByOrderByNombreAsc().stream().filter(InventarioItem::isActivo).toList();
        Map<String, List<InventarioItem>> m = new LinkedHashMap<>();
        m.put("agotados", activos.stream().filter(i -> "AGOTADO".equals(i.getEstadoExistencia())).toList());
        m.put("bajos", activos.stream().filter(i -> "BAJO".equals(i.getEstadoExistencia())).toList());
        m.put("porVencer", activos.stream().filter(i -> "POR_VENCER".equals(i.getEstadoVencimiento())).toList());
        m.put("vencidos", activos.stream().filter(i -> "VENCIDO".equals(i.getEstadoVencimiento())).toList());
        return m;
    }

    public List<UnidadMedida> unidades() {
        return unidades.findAllByOrderByTipoAscFactorAsc();
    }

    static String formato(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    Instant ahora() {
        return Instant.now();
    }
}
