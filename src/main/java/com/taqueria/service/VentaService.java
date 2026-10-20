package com.taqueria.service;

import com.taqueria.dto.Dtos.*;
import com.taqueria.dto.Vistas.PagoVista;
import com.taqueria.dto.Vistas.VentaVista;
import com.taqueria.model.*;
import com.taqueria.repository.*;
import com.taqueria.security.UsuarioActual;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Ventas y pagos. Registrar una venta guarda en una sola transaccion: la venta y sus
 * detalles, el descuento de inventario, los pagos, los movimientos de caja y el
 * comprobante. Si algo falla no queda nada registrado a medias.
 */
@Service
public class VentaService {

    public static final String REF_PAGO = "PAGO";

    private final VentaRepository ventas;
    private final PagoRepository pagos;
    private final PedidoService pedidoService;
    private final ProductoRepository productos;
    private final ClienteRepository clientes;
    private final InventarioService inventario;
    private final RecetaService recetas;
    private final CajaService caja;
    private final ComprobanteService comprobantes;
    private final ComprobanteRepository comprobanteRepo;
    private final ConfiguracionService configuracion;
    private final UsuarioService usuarios;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public VentaService(VentaRepository ventas, PagoRepository pagos, PedidoService pedidoService,
                        ProductoRepository productos, ClienteRepository clientes, InventarioService inventario,
                        RecetaService recetas, CajaService caja, ComprobanteService comprobantes,
                        ComprobanteRepository comprobanteRepo, ConfiguracionService configuracion,
                        UsuarioService usuarios, AuditoriaService auditoria, ApplicationEventPublisher eventos) {
        this.ventas = ventas;
        this.pagos = pagos;
        this.pedidoService = pedidoService;
        this.productos = productos;
        this.clientes = clientes;
        this.inventario = inventario;
        this.recetas = recetas;
        this.caja = caja;
        this.comprobantes = comprobantes;
        this.comprobanteRepo = comprobanteRepo;
        this.configuracion = configuracion;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    // ---------- Registro de ventas ----------

    /** Convierte un pedido en venta. Un pedido solo puede tener una venta. */
    @Transactional
    public VentaVista cobrarPedido(Long pedidoId, CobroRequest req, String clave) {
        Optional<Venta> repetida = buscarPorClave(clave);
        if (repetida.isPresent()) return vista(repetida.get());
        Pedido pedido = pedidoService.bloquear(pedidoId);
        Optional<Venta> existente = ventas.findByPedidoId(pedidoId);
        if (existente.isPresent()) {
            throw ApiException.conflicto("Este pedido ya fue registrado como la venta " + existente.get().getNumero()
                    + ". Abra esa venta para consultarla o imprimir su comprobante.");
        }
        if (pedido.getEstado() == EstadoPedido.CANCELADO) throw ApiException.conflicto("El pedido está cancelado");

        Venta v = nuevaVenta(req, clave);
        v.setPedidoId(pedido.getId());
        if (v.getClienteId() == null && pedido.getClienteId() != null) v.setClienteId(pedido.getClienteId());
        if (v.getClienteNombre() == null) v.setClienteNombre(pedido.getClienteNombre());
        if (v.getClienteDireccion() == null) v.setClienteDireccion(pedido.getDireccionEntrega());
        for (PedidoDetalle d : pedido.getDetalles()) {
            String descripcion = d.getNombreProducto();
            if (!d.getComplementos().isEmpty()) {
                descripcion += " + " + String.join(", ", d.getComplementos().stream().map(PedidoDetalleComplemento::getNombre).toList());
            }
            v.agregarDetalle(detalle(d.getProductoId(), descripcion, d.getCantidad(), d.getPrecioUnitario(), d.getObservaciones()));
        }
        return registrar(v, req, "Venta del pedido " + pedido.getNumero());
    }

    /** Venta de mostrador sin pedido previo (por ejemplo, una bebida). */
    @Transactional
    public VentaVista ventaDirecta(VentaDirectaRequest req, String clave) {
        Optional<Venta> repetida = buscarPorClave(clave);
        if (repetida.isPresent()) return vista(repetida.get());
        Venta v = nuevaVenta(req.cobro(), clave);
        for (ItemPedidoRequest item : req.items()) {
            PedidoDetalle d = pedidoService.construirDetalle(item);
            String descripcion = d.getNombreProducto();
            if (!d.getComplementos().isEmpty()) {
                descripcion += " + " + String.join(", ", d.getComplementos().stream().map(PedidoDetalleComplemento::getNombre).toList());
            }
            v.agregarDetalle(detalle(d.getProductoId(), descripcion, d.getCantidad(), d.getPrecioUnitario(), d.getObservaciones()));
        }
        return registrar(v, req.cobro(), "Venta directa de mostrador");
    }

    private Venta nuevaVenta(CobroRequest req, String clave) {
        Venta v = new Venta();
        v.setEstado(EstadoVenta.PENDIENTE_PAGO);
        v.setUsuarioId(UsuarioActual.id());
        v.setClaveIdempotencia(clave);
        if (req.clienteId() != null) {
            Cliente c = clientes.findById(req.clienteId()).orElseThrow(() -> ApiException.noEncontrado("Cliente"));
            v.setClienteId(c.getId());
            v.setClienteNombre(c.getNombre());
            v.setClienteDocumento(c.getDocumento());
            v.setClienteDireccion(c.getDireccion());
        }
        if (!Texto.vacio(req.clienteNombre())) v.setClienteNombre(req.clienteNombre().trim());
        if (!Texto.vacio(req.clienteDocumento())) v.setClienteDocumento(req.clienteDocumento().trim());
        if (!Texto.vacio(req.clienteDireccion())) v.setClienteDireccion(req.clienteDireccion().trim());
        return v;
    }

    private VentaDetalle detalle(Long productoId, String descripcion, int cantidad, BigDecimal precioUnitario, String obs) {
        Producto p = productos.findById(productoId).orElseThrow(() -> ApiException.noEncontrado("Producto"));
        VentaDetalle d = new VentaDetalle();
        d.setProductoId(productoId);
        d.setDescripcion(descripcion.length() > 250 ? descripcion.substring(0, 250) : descripcion);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(Dinero.r(precioUnitario));
        d.setCostoUnitario(costoUnitario(p));
        d.setSubtotal(Dinero.r(d.getPrecioUnitario().multiply(BigDecimal.valueOf(cantidad))));
        d.setObservaciones(obs);
        return d;
    }

    /** Costo estimado: el configurado en el producto o, si es cero, el de su receta. */
    private BigDecimal costoUnitario(Producto p) {
        if (p.getCostoEstimado() != null && p.getCostoEstimado().signum() > 0) return p.getCostoEstimado();
        if (p.getTipo() == TipoProducto.REVENTA && p.getItemInventario() != null) {
            return Dinero.r(p.getItemInventario().getCostoUnitario());
        }
        return recetas.obtener(p.getId()).map(recetas::costo).orElse(Dinero.r(BigDecimal.ZERO));
    }

    /** Calcula importes en el servidor, descuenta inventario, aplica pagos y emite el comprobante. */
    private VentaVista registrar(Venta v, CobroRequest req, String concepto) {
        BigDecimal subtotal = v.getDetalles().stream().map(VentaDetalle::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal descuento = Dinero.r(Dinero.nz(req.descuento()));
        if (descuento.compareTo(subtotal) > 0) throw new ApiException("El descuento no puede ser mayor que el subtotal");
        if (descuento.signum() > 0 && Texto.vacio(req.motivoDescuento())) {
            throw new ApiException("Indique el motivo del descuento");
        }
        BigDecimal total = Dinero.r(subtotal.subtract(descuento));
        BigDecimal iva = configuracion.obtener().getIvaPorcentaje();
        BigDecimal impuesto = Dinero.ivaIncluido(total, iva);
        v.setSubtotal(Dinero.r(subtotal));
        v.setDescuento(descuento);
        v.setTotal(total);
        v.setIvaPorcentaje(iva);
        v.setImpuesto(impuesto);
        v.setBaseImponible(Dinero.r(total.subtract(impuesto)));
        v.setCostoEstimado(Dinero.r(v.getDetalles().stream()
                .map(d -> d.getCostoUnitario().multiply(BigDecimal.valueOf(d.getCantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)));

        List<PagoRequest> listaPagos = req.pagos() == null ? List.of() : req.pagos();
        BigDecimal sumaPagos = listaPagos.stream().map(p -> Dinero.r(p.monto())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sumaPagos.compareTo(total) > 0) {
            throw new ApiException("Los pagos ($" + sumaPagos + ") superan el total de la venta ($" + total + ")");
        }
        try {
            ventas.saveAndFlush(v);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("La venta ya fue registrada");
        }
        List<String> sinReceta = inventario.descontarVenta(v);
        int i = 0;
        for (PagoRequest pr : listaPagos) {
            String clavePago = v.getClaveIdempotencia() == null ? null
                    : v.getClaveIdempotencia().substring(0, Math.min(60, v.getClaveIdempotencia().length())) + "-" + i;
            i++;
            aplicarPago(v, pr, clavePago);
        }
        Comprobante c = comprobantes.emitir(v);
        auditoria.registrar("Venta registrada", "Venta", v.getId(), v.getNumero() + " - " + concepto + " - total $"
                + v.getTotal() + (descuento.signum() > 0 ? ", descuento $" + descuento + " (" + req.motivoDescuento().trim() + ")" : "")
                + " - comprobante " + c.getNumero());
        if (v.getPedidoId() != null) {
            eventos.publishEvent(new PedidoEventos.PedidoCambiado(v.getPedidoId(), String.format("P-%06d", v.getPedidoId()), "venta"));
        }
        VentaVista vista = vista(v);
        return withSinReceta(vista, sinReceta);
    }

    // ---------- Pagos ----------

    /** Registra un pago de una venta pendiente. */
    @Transactional
    public VentaVista registrarPago(Long ventaId, PagoRequest req, String clave) {
        if (clave != null && pagos.findByClaveIdempotencia(clave).isPresent()) {
            return vista(obtener(ventaId));
        }
        Venta v = ventas.bloquear(ventaId).orElseThrow(() -> ApiException.noEncontrado("Venta"));
        aplicarPago(v, req, clave);
        return vista(v);
    }

    private void aplicarPago(Venta v, PagoRequest req, String clave) {
        if (v.getEstado() == EstadoVenta.ANULADA) throw ApiException.conflicto("La venta está anulada");
        if (v.getEstado() == EstadoVenta.PAGADA) throw ApiException.conflicto("La venta ya está pagada");
        BigDecimal monto = Dinero.r(req.monto());
        if (monto.compareTo(v.getSaldo()) > 0) {
            throw new ApiException("El pago ($" + monto + ") es mayor que el saldo pendiente ($" + v.getSaldo() + ")");
        }
        Caja cajaAbierta = caja.requerirAbierta();
        Pago p = new Pago();
        p.setVentaId(v.getId());
        p.setMetodo(req.metodo());
        p.setMonto(monto);
        String referencia = Texto.limpio(req.referencia());
        // No se guardan datos de tarjetas: se rechaza algo que parezca un numero de tarjeta completo
        if (referencia != null && referencia.replaceAll("[\\s-]", "").matches(".*\\d{13,19}.*")) {
            throw new ApiException("No escriba el número completo de la tarjeta. Anote solo la autorización o los últimos 4 dígitos.");
        }
        p.setReferencia(referencia);
        if (req.metodo() == MetodoPago.EFECTIVO) {
            BigDecimal recibido = req.montoRecibido() == null ? monto : Dinero.r(req.montoRecibido());
            if (recibido.compareTo(monto) < 0) {
                throw new ApiException("El efectivo recibido ($" + recibido + ") es menor que el monto a pagar ($" + monto + ")");
            }
            p.setMontoRecibido(recibido);
            p.setCambio(Dinero.r(recibido.subtract(monto)));
        }
        p.setCajaId(cajaAbierta.getId());
        p.setUsuarioId(UsuarioActual.id());
        p.setClaveIdempotencia(clave);
        try {
            pagos.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("Este pago ya fue registrado");
        }
        caja.registrar(cajaAbierta, TipoMovimientoCaja.VENTA, req.metodo(), monto,
                "Pago de la venta " + v.getNumero(), REF_PAGO, p.getId());
        v.setMontoPagado(Dinero.r(v.getMontoPagado().add(monto)));
        if (v.getSaldo().signum() == 0) v.setEstado(EstadoVenta.PAGADA);
        ventas.save(v);
        auditoria.registrar("Pago registrado", "Venta", v.getId(), v.getNumero() + ": " + req.metodo() + " $" + monto
                + (p.getCambio() != null && p.getCambio().signum() > 0 ? ", cambio $" + p.getCambio() : ""));
    }

    // ---------- Anulaciones ----------

    /**
     * Anula una venta con motivo. Los pagos se revierten como devolucion en la caja abierta;
     * el inventario se devuelve solo si se indica (por ejemplo, si el producto no se preparo).
     */
    @Transactional
    public VentaVista anular(Long ventaId, AnulacionRequest req) {
        Venta v = ventas.bloquear(ventaId).orElseThrow(() -> ApiException.noEncontrado("Venta"));
        if (v.getEstado() == EstadoVenta.ANULADA) throw ApiException.conflicto("La venta ya estaba anulada");
        String motivo = req.motivo().trim();
        List<Pago> aplicados = pagos.findByVentaIdOrderByCreadoEnAsc(v.getId()).stream()
                .filter(p -> p.getEstado() == EstadoPago.APLICADO).toList();
        if (!aplicados.isEmpty()) {
            Caja cajaAbierta = caja.requerirAbierta();
            BigDecimal efectivo = aplicados.stream().filter(p -> p.getMetodo() == MetodoPago.EFECTIVO)
                    .map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (efectivo.signum() > 0) caja.validarEfectivoDisponible(cajaAbierta, efectivo);
            for (Pago p : aplicados) {
                p.setEstado(EstadoPago.REVERTIDO);
                pagos.save(p);
                caja.registrar(cajaAbierta, TipoMovimientoCaja.DEVOLUCION, p.getMetodo(), p.getMonto().negate(),
                        "Devolución por anulación de " + v.getNumero() + ": " + motivo, REF_PAGO, p.getId());
            }
        }
        if (req.devolverInventario()) {
            inventario.devolverVenta(v, motivo);
            v.setInventarioDevuelto(true);
        }
        comprobantes.anular(v.getId());
        v.setEstado(EstadoVenta.ANULADA);
        v.setAnuladaEn(Instant.now());
        v.setAnuladaPor(UsuarioActual.id());
        v.setMotivoAnulacion(motivo);
        ventas.save(v);
        if (v.getPedidoId() != null) {
            Pedido pedido = pedidoService.bloquear(v.getPedidoId());
            pedidoService.marcarCancelado(pedido, "Venta " + v.getNumero() + " anulada: " + motivo);
        }
        auditoria.registrar("Venta anulada", "Venta", v.getId(), v.getNumero() + ": " + motivo
                + (aplicados.isEmpty() ? "" : " - pagos devueltos: " + aplicados.size())
                + (req.devolverInventario() ? " - inventario devuelto" : " - inventario no devuelto (merma)"));
        return vista(v);
    }

    // ---------- Consultas ----------

    public Venta obtener(Long id) {
        return ventas.findById(id).orElseThrow(() -> ApiException.noEncontrado("Venta"));
    }

    private Optional<Venta> buscarPorClave(String clave) {
        return clave == null ? Optional.empty() : ventas.findByClaveIdempotencia(clave);
    }

    @Transactional(readOnly = true)
    public Page<VentaVista> buscar(String numero, EstadoVenta estado, Long usuarioId, LocalDate desde, LocalDate hasta,
                                   int pagina, int tamano) {
        // Al buscar por numero se revisa todo el historial (hasta 3 años) si no se indica fecha
        LocalDate d = Fechas.o(desde, Texto.vacio(numero) ? Fechas.hoy().minusDays(7) : Fechas.hoy().minusYears(3));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        return ventas.buscar(PedidoService.numeroAId(numero, "V"), estado, usuarioId, Fechas.inicio(d), Fechas.finExclusivo(h),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100))).map(this::vista);
    }

    public List<VentaVista> pendientesDePago() {
        return ventas.findByEstadoOrderByCreadoEnAsc(EstadoVenta.PENDIENTE_PAGO).stream().map(this::vista).toList();
    }

    @Transactional(readOnly = true)
    public VentaVista vista(Long id) {
        return vista(obtener(id));
    }

    VentaVista vista(Venta v) {
        Map<Long, String> nombres = usuarios.nombres();
        List<PagoVista> listaPagos = pagos.findByVentaIdOrderByCreadoEnAsc(v.getId()).stream()
                .map(p -> new PagoVista(p.getId(), p.getMetodo(), p.getMonto(), p.getMontoRecibido(), p.getCambio(),
                        p.getReferencia(), p.getEstado(), nombres.get(p.getUsuarioId()), p.getCreadoEn()))
                .toList();
        Comprobante c = comprobanteRepo.findByVentaId(v.getId()).orElse(null);
        TipoPedido tipo = v.getPedidoId() == null ? null : pedidoService.obtener(v.getPedidoId()).getTipo();
        return new VentaVista(v.getId(), v.getNumero(), v.getPedidoId(),
                v.getPedidoId() == null ? null : String.format("P-%06d", v.getPedidoId()), tipo, v.getEstado(),
                v.getClienteId(), v.getClienteNombre(), v.getClienteDocumento(), v.getClienteDireccion(), v.getSubtotal(),
                v.getDescuento(), v.getBaseImponible(), v.getIvaPorcentaje(), v.getImpuesto(), v.getTotal(),
                v.getMontoPagado(), v.getSaldo(), v.getCostoEstimado(), nombres.get(v.getUsuarioId()), v.getCreadoEn(),
                v.getAnuladaEn(), v.getAnuladaPor() == null ? null : nombres.get(v.getAnuladaPor()), v.getMotivoAnulacion(),
                v.isInventarioDevuelto(), v.getDetalles(), listaPagos, c == null ? null : c.getId(),
                c == null ? null : c.getNumero(), List.of());
    }

    private static VentaVista withSinReceta(VentaVista x, List<String> sinReceta) {
        return new VentaVista(x.id(), x.numero(), x.pedidoId(), x.pedidoNumero(), x.tipoPedido(), x.estado(), x.clienteId(),
                x.clienteNombre(), x.clienteDocumento(), x.clienteDireccion(), x.subtotal(), x.descuento(), x.baseImponible(),
                x.ivaPorcentaje(), x.impuesto(), x.total(), x.montoPagado(), x.saldo(), x.costoEstimado(), x.usuario(),
                x.creadoEn(), x.anuladaEn(), x.anuladaPor(), x.motivoAnulacion(), x.inventarioDevuelto(), x.detalles(),
                x.pagos(), x.comprobanteId(), x.comprobanteNumero(), sinReceta);
    }
}
