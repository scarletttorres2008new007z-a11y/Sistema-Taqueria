package com.taqueria.service;

import com.taqueria.dto.Dtos.ItemPedidoRequest;
import com.taqueria.dto.Dtos.PedidoRequest;
import com.taqueria.dto.Vistas.HistorialEstado;
import com.taqueria.dto.Vistas.PedidoVista;
import com.taqueria.dto.Vistas.VentaResumen;
import com.taqueria.model.*;
import com.taqueria.repository.*;
import com.taqueria.security.UsuarioActual;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Pedidos: lo que el cliente pide y la cocina prepara. El cobro se registra aparte como
 * venta (VentaService); el inventario se descuenta cuando la venta se confirma.
 */
@Service
public class PedidoService {

    public static final Set<EstadoPedido> EN_COCINA =
            EnumSet.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);

    private final PedidoRepository pedidos;
    private final PedidoEstadoRepository historial;
    private final ProductoRepository productos;
    private final ComplementoRepository complementos;
    private final MesaRepository mesas;
    private final ClienteRepository clientes;
    private final VentaRepository ventas;
    private final PagoRepository pagos;
    private final ComprobanteRepository comprobantes;
    private final InventarioService inventario;
    private final UsuarioService usuarios;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;

    public PedidoService(PedidoRepository pedidos, PedidoEstadoRepository historial, ProductoRepository productos,
                         ComplementoRepository complementos, MesaRepository mesas, ClienteRepository clientes,
                         VentaRepository ventas, PagoRepository pagos, ComprobanteRepository comprobantes,
                         InventarioService inventario, UsuarioService usuarios, AuditoriaService auditoria,
                         ApplicationEventPublisher eventos) {
        this.pedidos = pedidos;
        this.historial = historial;
        this.productos = productos;
        this.complementos = complementos;
        this.mesas = mesas;
        this.clientes = clientes;
        this.ventas = ventas;
        this.pagos = pagos;
        this.comprobantes = comprobantes;
        this.inventario = inventario;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    // ---------- Registro ----------

    @Transactional
    public Pedido crear(PedidoRequest req, String claveIdempotencia) {
        if (claveIdempotencia != null) {
            Optional<Pedido> previo = pedidos.findByClaveIdempotencia(claveIdempotencia);
            if (previo.isPresent()) return previo.get();
        }
        Pedido p = new Pedido();
        p.setTipo(req.tipo());
        p.setUsuarioId(UsuarioActual.id());
        p.setClaveIdempotencia(claveIdempotencia);
        p.setNotas(Texto.limpio(req.notas()));

        Cliente cliente = req.clienteId() == null ? null
                : clientes.findById(req.clienteId()).orElseThrow(() -> ApiException.noEncontrado("Cliente"));
        p.setClienteId(cliente == null ? null : cliente.getId());
        p.setClienteNombre(Optional.ofNullable(Texto.limpio(req.clienteNombre())).orElse(cliente == null ? null : cliente.getNombre()));
        p.setClienteTelefono(Optional.ofNullable(Texto.limpio(req.clienteTelefono())).orElse(cliente == null ? null : cliente.getTelefono()));

        switch (req.tipo()) {
            case LOCAL -> {
                if (req.mesaId() != null) {
                    Mesa mesa = mesas.findById(req.mesaId()).orElseThrow(() -> ApiException.noEncontrado("Mesa"));
                    if (!mesa.isActiva()) throw new ApiException("La mesa " + mesa.getNombre() + " no está activa");
                    p.setMesa(mesa);
                } else if (mesas.findAll().stream().anyMatch(Mesa::isActiva)) {
                    throw new ApiException("Seleccione la mesa del pedido");
                }
            }
            case DOMICILIO -> {
                String direccion = Optional.ofNullable(Texto.limpio(req.direccionEntrega()))
                        .orElse(cliente == null ? null : cliente.getDireccion());
                if (Texto.vacio(p.getClienteNombre()) || Texto.vacio(p.getClienteTelefono()) || Texto.vacio(direccion)) {
                    throw new ApiException("Para entrega a domicilio indique nombre, teléfono y dirección del cliente");
                }
                p.setDireccionEntrega(direccion);
                p.setReferenciaEntrega(Texto.limpio(req.referenciaEntrega()));
            }
            case LLEVAR -> {
                // Para llevar no requiere datos adicionales.
            }
        }

        for (ItemPedidoRequest item : req.items()) {
            p.agregarDetalle(construirDetalle(item));
        }
        recalcular(p);
        inventario.verificarDisponibilidad(p.getDetalles().stream()
                .map(d -> new InventarioService.LineaConsumo(d.getProductoId(), d.getCantidad())).toList());

        pedidos.saveAndFlush(p);
        registrarHistorial(p.getId(), null, EstadoPedido.PENDIENTE, "Pedido registrado");
        auditoria.registrar("Pedido registrado", "Pedido", p.getId(),
                p.getNumero() + " " + p.getTipo() + " - " + p.getDetalles().size() + " producto(s) - $" + p.getTotal());
        eventos.publishEvent(new PedidoEventos.PedidoCambiado(p.getId(), p.getNumero(), "creado"));
        return p;
    }

    /** Crea la linea con el precio vigente del producto y sus complementos (precio historico). */
    PedidoDetalle construirDetalle(ItemPedidoRequest item) {
        Producto prod = productos.findById(item.productoId())
                .orElseThrow(() -> ApiException.noEncontrado("Producto " + item.productoId()));
        if (!prod.isActivo() || !prod.isDisponible()) {
            throw new ApiException(prod.getNombre() + " no está disponible en este momento");
        }
        PedidoDetalle d = new PedidoDetalle();
        d.setProductoId(prod.getId());
        d.setNombreProducto(prod.getNombre());
        d.setPrecioBase(prod.getPrecio());
        d.setCantidad(item.cantidad());
        d.setObservaciones(Texto.limpio(item.observaciones()));
        BigDecimal extras = BigDecimal.ZERO;
        if (item.complementoIds() != null) {
            for (Long cid : new LinkedHashSet<>(item.complementoIds())) {
                Complemento c = complementos.findById(cid).orElseThrow(() -> ApiException.noEncontrado("Complemento"));
                if (!c.isActivo()) throw new ApiException("El complemento " + c.getNombre() + " no está disponible");
                if (c.getCategoria() != null && !c.getCategoria().getId().equals(prod.getCategoria().getId())) {
                    throw new ApiException("El complemento " + c.getNombre() + " no aplica a " + prod.getNombre());
                }
                PedidoDetalleComplemento pc = new PedidoDetalleComplemento();
                pc.setDetalle(d);
                pc.setComplementoId(c.getId());
                pc.setNombre(c.getNombre());
                pc.setPrecioExtra(c.getPrecioExtra());
                d.getComplementos().add(pc);
                extras = extras.add(c.getPrecioExtra());
            }
        }
        d.setPrecioUnitario(Dinero.r(prod.getPrecio().add(extras)));
        d.setSubtotal(Dinero.r(d.getPrecioUnitario().multiply(BigDecimal.valueOf(item.cantidad()))));
        return d;
    }

    void recalcular(Pedido p) {
        BigDecimal subtotal = p.getDetalles().stream().map(PedidoDetalle::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        p.setSubtotal(Dinero.r(subtotal));
        p.setTotal(Dinero.r(subtotal));
    }

    // ---------- Estados ----------

    @Transactional
    public Pedido cambiarEstado(Long id, EstadoPedido nuevo, String motivo) {
        if (nuevo == EstadoPedido.CANCELADO) {
            throw new ApiException("Use la opción Cancelar pedido e indique el motivo");
        }
        Pedido p = bloquear(id);
        EstadoPedido anterior = p.getEstado();
        if (anterior == nuevo) return p;
        if (!anterior.puedeCambiarA(nuevo)) {
            throw ApiException.conflicto("No se puede pasar un pedido de " + nombreEstado(anterior) + " a " + nombreEstado(nuevo));
        }
        p.setEstado(nuevo);
        pedidos.save(p);
        registrarHistorial(p.getId(), anterior, nuevo, Texto.limpio(motivo));
        auditoria.registrar("Cambio de estado de pedido", "Pedido", p.getId(), p.getNumero() + ": " + anterior + " -> " + nuevo);
        eventos.publishEvent(new PedidoEventos.PedidoCambiado(p.getId(), p.getNumero(), "estado"));
        return p;
    }

    /** Cancela un pedido que todavia no tiene una venta registrada. */
    @Transactional
    public Pedido cancelar(Long id, String motivo) {
        Pedido p = bloquear(id);
        ventas.findByPedidoId(id).filter(v -> v.getEstado() != EstadoVenta.ANULADA).ifPresent(v -> {
            throw ApiException.conflicto("El pedido ya tiene la venta " + v.getNumero()
                    + ". Para cancelarlo, un usuario autorizado debe anular la venta.");
        });
        if (!p.getEstado().puedeCambiarA(EstadoPedido.CANCELADO)) {
            throw ApiException.conflicto("Un pedido " + nombreEstado(p.getEstado()) + " no se puede cancelar");
        }
        marcarCancelado(p, motivo);
        return p;
    }

    /** Usado al cancelar un pedido o al anular su venta. El pedido debe estar bloqueado. */
    void marcarCancelado(Pedido p, String motivo) {
        EstadoPedido anterior = p.getEstado();
        if (anterior == EstadoPedido.CANCELADO) return;
        p.setEstado(EstadoPedido.CANCELADO);
        pedidos.save(p);
        registrarHistorial(p.getId(), anterior, EstadoPedido.CANCELADO, motivo);
        auditoria.registrar("Pedido cancelado", "Pedido", p.getId(), p.getNumero() + ": " + motivo);
        eventos.publishEvent(new PedidoEventos.PedidoCambiado(p.getId(), p.getNumero(), "cancelado"));
    }

    Pedido bloquear(Long id) {
        return pedidos.bloquear(id).orElseThrow(() -> ApiException.noEncontrado("Pedido"));
    }

    private void registrarHistorial(Long pedidoId, EstadoPedido anterior, EstadoPedido nuevo, String motivo) {
        PedidoEstado h = new PedidoEstado();
        h.setPedidoId(pedidoId);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setMotivo(motivo);
        h.setUsuarioId(UsuarioActual.id());
        historial.save(h);
    }

    // ---------- Consultas ----------

    public Pedido obtener(Long id) {
        return pedidos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Pedido"));
    }

    @Transactional(readOnly = true)
    public PedidoVista vista(Long id) {
        return vistas(List.of(obtener(id))).get(0);
    }

    @Transactional(readOnly = true)
    public List<PedidoVista> cocina() {
        return vistas(pedidos.findByEstadoInOrderByCreadoEnAsc(EN_COCINA));
    }

    @Transactional(readOnly = true)
    public Page<PedidoVista> buscar(String numero, EstadoPedido estado, TipoPedido tipo, LocalDate desde, LocalDate hasta,
                                    int pagina, int tamano) {
        // Al buscar por numero se revisa todo el historial (hasta 3 años) si no se indica fecha
        LocalDate d = Fechas.o(desde, Texto.vacio(numero) ? Fechas.hoy().minusDays(7) : Fechas.hoy().minusYears(3));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        Long id = numeroAId(numero, "P");
        Page<Pedido> page = pedidos.buscar(id, estado, tipo, Fechas.inicio(d), Fechas.finExclusivo(h),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100)));
        List<PedidoVista> vistas = vistas(page.getContent());
        return page.map(p -> vistas.stream().filter(v -> v.id().equals(p.getId())).findFirst().orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<HistorialEstado> historial(Long pedidoId) {
        Map<Long, String> nombres = usuarios.nombres();
        return historial.findByPedidoIdOrderByCreadoEnAscIdAsc(pedidoId).stream()
                .map(h -> new HistorialEstado(h.getEstadoAnterior(), h.getEstadoNuevo(), h.getMotivo(),
                        nombres.get(h.getUsuarioId()), h.getCreadoEn()))
                .toList();
    }

    List<PedidoVista> vistas(List<Pedido> lista) {
        Map<Long, String> nombres = usuarios.nombres();
        List<PedidoVista> res = new ArrayList<>();
        for (Pedido p : lista) {
            VentaResumen venta = ventas.findByPedidoId(p.getId()).map(this::resumenVenta).orElse(null);
            List<EstadoPedido> siguientes = new ArrayList<>(p.getEstado().siguientes());
            siguientes.remove(EstadoPedido.CANCELADO);
            res.add(new PedidoVista(p.getId(), p.getNumero(), p.getTipo(), p.getEstado(),
                    p.getMesa() == null ? null : p.getMesa().getNombre(), p.getClienteId(), p.getClienteNombre(),
                    p.getClienteTelefono(), p.getDireccionEntrega(), p.getReferenciaEntrega(), p.getNotas(),
                    p.getSubtotal(), p.getTotal(), p.getDetalles().stream().mapToInt(PedidoDetalle::getCantidad).sum(),
                    p.getCreadoEn(), p.getActualizadoEn(), nombres.get(p.getUsuarioId()), p.getDetalles(), siguientes, venta));
        }
        return res;
    }

    VentaResumen resumenVenta(Venta v) {
        String metodos = pagos.findByVentaIdOrderByCreadoEnAsc(v.getId()).stream()
                .filter(pg -> pg.getEstado() == EstadoPago.APLICADO)
                .map(pg -> pg.getMetodo().name()).distinct().reduce((a, b) -> a + ", " + b).orElse(null);
        Comprobante c = comprobantes.findByVentaId(v.getId()).orElse(null);
        return new VentaResumen(v.getId(), v.getNumero(), v.getEstado(), v.getTotal(), v.getSaldo(), metodos,
                c == null ? null : c.getId(), c == null ? null : c.getNumero());
    }

    /** Acepta "P-000012", "12" o vacio. */
    static Long numeroAId(String numero, String prefijo) {
        if (Texto.vacio(numero)) return null;
        String limpio = numero.trim().toUpperCase().replaceFirst("^" + prefijo + "-?", "");
        try {
            return Long.parseLong(limpio);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    static String nombreEstado(EstadoPedido e) {
        return switch (e) {
            case PENDIENTE -> "Pendiente";
            case EN_PREPARACION -> "En preparación";
            case LISTO -> "Listo para entregar";
            case ENTREGADO -> "Entregado";
            case CANCELADO -> "Cancelado";
        };
    }
}
