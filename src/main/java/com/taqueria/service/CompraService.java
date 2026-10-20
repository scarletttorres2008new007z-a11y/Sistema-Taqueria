package com.taqueria.service;

import com.taqueria.dto.Dtos.*;
import com.taqueria.model.*;
import com.taqueria.repository.CompraRepository;
import com.taqueria.repository.InventarioItemRepository;
import com.taqueria.repository.ProveedorRepository;
import com.taqueria.security.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Proveedores, compras de insumos, su recepcion en inventario y los pagos (deudas). */
@Service
public class CompraService {

    public static final String REF_PAGO_COMPRA = "PAGO_COMPRA";

    private final CompraRepository compras;
    private final ProveedorRepository proveedores;
    private final InventarioItemRepository items;
    private final InventarioService inventario;
    private final CajaService caja;
    private final AuditoriaService auditoria;

    public CompraService(CompraRepository compras, ProveedorRepository proveedores, InventarioItemRepository items,
                         InventarioService inventario, CajaService caja, AuditoriaService auditoria) {
        this.compras = compras;
        this.proveedores = proveedores;
        this.items = items;
        this.inventario = inventario;
        this.caja = caja;
        this.auditoria = auditoria;
    }

    // ---------- Proveedores ----------

    public List<Proveedor> proveedores() {
        return proveedores.findAllByOrderByNombreComercialAsc();
    }

    @Transactional
    public Proveedor guardarProveedor(Long id, ProveedorRequest req) {
        Proveedor p = id == null ? new Proveedor() : proveedores.findById(id).orElseThrow(() -> ApiException.noEncontrado("Proveedor"));
        p.setNombreComercial(req.nombreComercial().trim());
        p.setContacto(Texto.limpio(req.contacto()));
        p.setTelefono(Texto.limpio(req.telefono()));
        p.setCorreo(Texto.limpio(req.correo()));
        p.setDireccion(Texto.limpio(req.direccion()));
        p.setNit(Texto.limpio(req.nit()));
        p.setNrc(Texto.limpio(req.nrc()));
        p.setNotas(Texto.limpio(req.notas()));
        p.setActivo(req.activo());
        proveedores.save(p);
        auditoria.registrar(id == null ? "Proveedor creado" : "Proveedor modificado", "Proveedor", p.getId(), p.getNombreComercial());
        return p;
    }

    // ---------- Compras ----------

    public Compra obtener(Long id) {
        return compras.findById(id).orElseThrow(() -> ApiException.noEncontrado("Compra"));
    }

    public List<Compra> buscar(LocalDate desde, LocalDate hasta, Long proveedorId) {
        LocalDate d = Fechas.o(desde, Fechas.hoy().minusDays(60));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        return compras.buscar(d, h, proveedorId);
    }

    @Transactional
    public Compra crear(CompraRequest req) {
        Proveedor prov = proveedores.findById(req.proveedorId()).orElseThrow(() -> ApiException.noEncontrado("Proveedor"));
        if (!prov.isActivo()) throw new ApiException("El proveedor está inactivo");
        Set<Long> vistos = new HashSet<>();
        Compra c = new Compra();
        c.setProveedor(prov);
        c.setFecha(req.fecha());
        c.setNumeroFactura(Texto.limpio(req.numeroFactura()));
        c.setFechaVencimientoPago(req.fechaVencimientoPago());
        c.setNotas(Texto.limpio(req.notas()));
        c.setUsuarioId(UsuarioActual.id());
        BigDecimal total = BigDecimal.ZERO;
        for (CompraDetalleRequest d : req.detalles()) {
            if (!vistos.add(d.itemId())) throw new ApiException("Un artículo aparece dos veces en la compra");
            InventarioItem it = items.findById(d.itemId()).orElseThrow(() -> ApiException.noEncontrado("Artículo"));
            CompraDetalle det = new CompraDetalle();
            det.setCompra(c);
            det.setItem(it);
            det.setCantidad(d.cantidad());
            det.setCostoUnitario(d.costoUnitario());
            det.setSubtotal(Dinero.r(d.cantidad().multiply(d.costoUnitario())));
            det.setLote(Texto.limpio(d.lote()));
            det.setFechaVencimiento(d.fechaVencimiento());
            c.getDetalles().add(det);
            total = total.add(det.getSubtotal());
        }
        c.setTotal(Dinero.r(total));
        compras.saveAndFlush(c);
        auditoria.registrar("Compra registrada", "Compra", c.getId(), c.getNumero() + " - " + prov.getNombreComercial()
                + " - $" + c.getTotal() + (c.getNumeroFactura() == null ? "" : " - factura " + c.getNumeroFactura()));
        if (req.recibirAhora()) return recibir(c.getId());
        return c;
    }

    /** Confirma la recepcion e incrementa el inventario. Una compra recibida no se puede recibir otra vez. */
    @Transactional
    public Compra recibir(Long id) {
        Compra c = compras.bloquear(id).orElseThrow(() -> ApiException.noEncontrado("Compra"));
        if (c.getEstado() == EstadoCompra.RECIBIDA) throw ApiException.conflicto("La compra " + c.getNumero() + " ya fue recibida");
        if (c.getEstado() == EstadoCompra.ANULADA) throw ApiException.conflicto("La compra está anulada");
        for (CompraDetalle d : c.getDetalles()) {
            inventario.aplicar(d.getItem().getId(), d.getCantidad(), TipoMovimientoInventario.COMPRA,
                    "Compra " + c.getNumero() + " a " + c.getProveedor().getNombreComercial(),
                    InventarioService.REF_COMPRA, c.getId(), d.getCostoUnitario());
            if (d.getLote() != null || d.getFechaVencimiento() != null) {
                InventarioItem it = inventario.item(d.getItem().getId());
                if (d.getLote() != null) it.setLote(d.getLote());
                if (d.getFechaVencimiento() != null) it.setFechaVencimiento(d.getFechaVencimiento());
                items.save(it);
            }
        }
        c.setEstado(EstadoCompra.RECIBIDA);
        c.setFechaRecepcion(Instant.now());
        c.setRecibidoPor(UsuarioActual.id());
        compras.save(c);
        auditoria.registrar("Compra recibida", "Compra", c.getId(), c.getNumero() + ": " + c.getDetalles().size() + " artículo(s) ingresados al inventario");
        return c;
    }

    @Transactional
    public Compra pagar(Long id, PagoCompraRequest req) {
        Compra c = compras.bloquear(id).orElseThrow(() -> ApiException.noEncontrado("Compra"));
        if (c.getEstado() == EstadoCompra.ANULADA) throw ApiException.conflicto("La compra está anulada");
        BigDecimal monto = Dinero.r(req.monto());
        if (monto.compareTo(c.getSaldo()) > 0) {
            throw new ApiException("El pago ($" + monto + ") supera el saldo pendiente ($" + c.getSaldo() + ")");
        }
        PagoCompra p = new PagoCompra();
        p.setCompra(c);
        p.setFecha(Fechas.o(req.fecha(), Fechas.hoy()));
        p.setMonto(monto);
        p.setMetodo(req.metodo());
        p.setDesdeCaja(req.desdeCaja());
        p.setNota(Texto.limpio(req.nota()));
        p.setUsuarioId(UsuarioActual.id());
        c.getPagos().add(p);
        c.setMontoPagado(Dinero.r(c.getMontoPagado().add(monto)));
        compras.saveAndFlush(c);
        if (req.desdeCaja()) {
            if (req.metodo() != MetodoPago.EFECTIVO) throw new ApiException("Solo los pagos en efectivo pueden salir de la caja");
            Caja abierta = caja.requerirAbierta();
            caja.validarEfectivoDisponible(abierta, monto);
            PagoCompra guardado = c.getPagos().get(c.getPagos().size() - 1);
            caja.registrar(abierta, TipoMovimientoCaja.PAGO_PROVEEDOR, MetodoPago.EFECTIVO, monto.negate(),
                    "Pago a " + c.getProveedor().getNombreComercial() + " (" + c.getNumero() + ")", REF_PAGO_COMPRA, guardado.getId());
        }
        auditoria.registrar("Pago a proveedor", "Compra", c.getId(), c.getNumero() + ": " + req.metodo() + " $" + monto
                + ", saldo $" + c.getSaldo());
        return c;
    }

    @Transactional
    public Compra anular(Long id, String motivo) {
        Compra c = compras.bloquear(id).orElseThrow(() -> ApiException.noEncontrado("Compra"));
        if (c.getEstado() == EstadoCompra.RECIBIDA) {
            throw ApiException.conflicto("La compra ya ingresó al inventario. Registre una salida de inventario si hubo devolución al proveedor.");
        }
        if (c.getMontoPagado().signum() > 0) throw ApiException.conflicto("La compra tiene pagos registrados");
        if (c.getEstado() == EstadoCompra.ANULADA) throw ApiException.conflicto("La compra ya estaba anulada");
        c.setEstado(EstadoCompra.ANULADA);
        c.setNotas((c.getNotas() == null ? "" : c.getNotas() + " | ") + "Anulada: " + motivo.trim());
        compras.save(c);
        auditoria.registrar("Compra anulada", "Compra", c.getId(), c.getNumero() + ": " + motivo.trim());
        return c;
    }

    /** Deudas pendientes agrupadas por proveedor. */
    public List<Map<String, Object>> deudas() {
        Map<Long, Map<String, Object>> porProveedor = new LinkedHashMap<>();
        for (Compra c : compras.conSaldo()) {
            Map<String, Object> fila = porProveedor.computeIfAbsent(c.getProveedor().getId(), k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("proveedorId", c.getProveedor().getId());
                m.put("proveedor", c.getProveedor().getNombreComercial());
                m.put("telefono", c.getProveedor().getTelefono());
                m.put("saldo", BigDecimal.ZERO);
                m.put("compras", new ArrayList<Map<String, Object>>());
                m.put("vencidas", 0);
                return m;
            });
            fila.put("saldo", ((BigDecimal) fila.get("saldo")).add(c.getSaldo()));
            if (c.isVencida()) fila.put("vencidas", (int) fila.get("vencidas") + 1);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lista = (List<Map<String, Object>>) fila.get("compras");
            Map<String, Object> det = new LinkedHashMap<>();
            det.put("id", c.getId());
            det.put("numero", c.getNumero());
            det.put("numeroFactura", c.getNumeroFactura());
            det.put("fecha", c.getFecha());
            det.put("total", c.getTotal());
            det.put("saldo", c.getSaldo());
            det.put("fechaVencimientoPago", c.getFechaVencimientoPago());
            det.put("vencida", c.isVencida());
            lista.add(det);
        }
        return new ArrayList<>(porProveedor.values());
    }
}
