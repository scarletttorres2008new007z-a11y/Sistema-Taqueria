package com.taqueria.dto;

import com.taqueria.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Respuestas de la API que combinan varias tablas. */
public final class Vistas {

    private Vistas() {
    }

    public record VentaResumen(Long id, String numero, EstadoVenta estado, BigDecimal total, BigDecimal saldo,
                               String metodosPago, Long comprobanteId, String comprobanteNumero) {
    }

    public record PedidoVista(Long id, String numero, TipoPedido tipo, EstadoPedido estado, String mesa,
                              Long clienteId, String clienteNombre, String clienteTelefono, String direccionEntrega,
                              String referenciaEntrega, String notas, BigDecimal subtotal, BigDecimal total,
                              int cantidadProductos, Instant creadoEn, Instant actualizadoEn, String registradoPor,
                              List<PedidoDetalle> detalles, List<EstadoPedido> siguientesEstados, VentaResumen venta) {
    }

    public record HistorialEstado(EstadoPedido estadoAnterior, EstadoPedido estadoNuevo, String motivo,
                                  String usuario, Instant fecha) {
    }

    public record PagoVista(Long id, MetodoPago metodo, BigDecimal monto, BigDecimal montoRecibido, BigDecimal cambio,
                            String referencia, EstadoPago estado, String usuario, Instant fecha) {
    }

    public record VentaVista(Long id, String numero, Long pedidoId, String pedidoNumero, TipoPedido tipoPedido,
                             EstadoVenta estado, Long clienteId, String clienteNombre, String clienteDocumento,
                             String clienteDireccion, BigDecimal subtotal, BigDecimal descuento, BigDecimal baseImponible,
                             BigDecimal ivaPorcentaje, BigDecimal impuesto, BigDecimal total, BigDecimal montoPagado, BigDecimal saldo,
                             BigDecimal costoEstimado, String usuario, Instant creadoEn, Instant anuladaEn,
                             String anuladaPor, String motivoAnulacion, boolean inventarioDevuelto,
                             List<VentaDetalle> detalles, List<PagoVista> pagos, Long comprobanteId,
                             String comprobanteNumero, List<String> productosSinReceta) {
    }

    public record ComprobanteVista(Comprobante comprobante, VentaVista venta, BigDecimal ivaPorcentaje,
                                   int anchoPapelMm, String logoUrl, String emitidoPor, String leyenda) {
    }

    public record CajaResumen(Caja caja, String abiertaPor, String cerradaPor, BigDecimal ventasEfectivo,
                              BigDecimal ventasTarjeta, BigDecimal ventasTransferencia, BigDecimal devoluciones,
                              BigDecimal ingresos, BigDecimal retiros, BigDecimal gastos, BigDecimal pagosProveedores,
                              BigDecimal efectivoEsperado, int cantidadPagos, List<MovimientoCajaVista> movimientos) {
    }

    public record MovimientoCajaVista(Long id, TipoMovimientoCaja tipo, MetodoPago metodo, BigDecimal monto,
                                      String concepto, String referenciaTipo, Long referenciaId, String usuario,
                                      Instant fecha) {
    }
}
