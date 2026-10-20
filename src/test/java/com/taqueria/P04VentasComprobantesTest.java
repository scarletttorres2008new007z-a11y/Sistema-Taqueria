package com.taqueria;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas 11 a 18: ventas, pagos, totales, comprobantes, reimpresion, inventario y anulaciones. */
@Order(4)
class P04VentasComprobantesTest extends PruebaBase {

    private long carne, tortilla, taco, horchata;

    /** Taco ($1.25) con receta de 100 g de carne y 2 tortillas; horchata ($1.00) sin receta. */
    @BeforeEach
    void menu() throws Exception {
        String s = "P04-" + System.nanoTime();
        carne = crearItem("Carne " + s, "kg", "10", "1", "6.00");
        tortilla = crearItem("Tortilla " + s, "u", "500", "10", "0.05");
        taco = crearProducto("Taco " + s, "Tacos", "1.25");
        receta(taco, carne, "100", "g", tortilla, "2", "u");
        horchata = crearProducto("Horchata " + s, "Bebidas", "1.00");
        asegurarCajaAbierta();
    }

    private Resp cobrar(long pedidoId, Map<String, Object> cobro) throws Exception {
        return post("CAJERO", "/api/pedidos/" + pedidoId + "/venta", cobro, clave());
    }

    private Map<String, Object> cobro(Object... pagos) {
        return m("descuento", null, "motivoDescuento", null, "pagos", List.of(pagos));
    }

    @Test
    void prueba11_ventaDesdeUnPedido() throws Exception {
        Resp pedido = crearPedidoLlevar(List.of(item(taco, 2), item(horchata, 1)));
        Resp venta = cobrar(pedido.id(), cobro(pagoEfectivo("3.50", "5.00")));
        assertThat(venta.estado()).isEqualTo(201);
        assertThat(venta.get("numero").asText()).startsWith("V-");
        assertThat(venta.get("estado").asText()).isEqualTo("PAGADA");
        assertThat(venta.get("pedidoId").asLong()).isEqualTo(pedido.id());
        assertThat(venta.get("detalles")).hasSize(2);
        assertThat(venta.get("usuario").asText()).isEqualTo("Prueba CAJERO");
        // El pedido queda enlazado a su venta
        assertThat(get("CAJERO", "/api/pedidos/" + pedido.id()).ok().get("venta").get("numero").asText())
                .isEqualTo(venta.get("numero").asText());
        // Venta directa de mostrador (sin pasar por cocina)
        Resp directa = post("CAJERO", "/api/ventas/directa", m("items", List.of(item(horchata, 2)),
                "cobro", cobro(m("metodo", "TARJETA", "monto", d("2.00"), "referencia", "Aut. 123456"))), clave());
        assertThat(directa.estado()).isEqualTo(201);
        assertThat(directa.get("pedidoId").isNull()).isTrue();
        assertThat(directa.get("estado").asText()).isEqualTo("PAGADA");
    }

    @Test
    void prueba12_pagos() throws Exception {
        // Efectivo con cambio
        Resp efectivo = cobrar(crearPedidoLlevar(List.of(item(taco, 2))).id(), cobro(pagoEfectivo("2.50", "20.00")));
        JsonNode pago = efectivo.get("pagos").get(0);
        assertThat(pago.get("cambio").decimalValue()).isEqualByComparingTo("17.50");
        assertThat(pago.get("montoRecibido").decimalValue()).isEqualByComparingTo("20.00");

        // Efectivo recibido menor que el monto se rechaza y no se registra nada
        long pid = crearPedidoLlevar(List.of(item(taco, 2))).id();
        long ventasAntes = contar("SELECT count(*) FROM ventas");
        Resp insuficiente = cobrar(pid, cobro(pagoEfectivo("2.50", "2.00")));
        assertThat(insuficiente.estado()).isEqualTo(400);
        assertThat(contar("SELECT count(*) FROM ventas")).isEqualTo(ventasAntes);
        // Pagos que superan el total
        assertThat(cobrar(pid, cobro(pagoEfectivo("3.00", null))).estado()).isEqualTo(400);

        // Pago mixto: tarjeta + efectivo
        Resp mixto = cobrar(pid, cobro(m("metodo", "TARJETA", "monto", d("1.50"), "referencia", "últimos 4: 4242"),
                pagoEfectivo("1.00", "1.00")));
        assertThat(mixto.get("estado").asText()).isEqualTo("PAGADA");
        assertThat(mixto.get("pagos")).hasSize(2);

        // Venta pendiente de pago y pagos parciales posteriores
        Resp pendiente = cobrar(crearPedidoLlevar(List.of(item(taco, 4))).id(), cobro());
        assertThat(pendiente.get("estado").asText()).isEqualTo("PENDIENTE_PAGO");
        assertThat(pendiente.dinero("saldo")).isEqualByComparingTo("5.00");
        long vid = pendiente.id();
        Resp parcial = post("CAJERO", "/api/ventas/" + vid + "/pagos", m("metodo", "TRANSFERENCIA", "monto", d("2.00"),
                "referencia", "Transferencia 998"), clave());
        assertThat(parcial.estado()).isEqualTo(201);
        assertThat(parcial.dinero("saldo")).isEqualByComparingTo("3.00");
        assertThat(post("CAJERO", "/api/ventas/" + vid + "/pagos", pagoEfectivo("3.01", null), clave()).estado())
                .as("no se puede pagar mas que el saldo").isEqualTo(400);
        Resp completo = post("CAJERO", "/api/ventas/" + vid + "/pagos", pagoEfectivo("3.00", "5.00"), clave()).ok();
        assertThat(completo.get("estado").asText()).isEqualTo("PAGADA");
        assertThat(completo.dinero("saldo")).isEqualByComparingTo("0");
        assertThat(post("CAJERO", "/api/ventas/" + vid + "/pagos", pagoEfectivo("1.00", null), clave()).estado()).isEqualTo(409);
        // Cada pago quedo en la caja
        assertThat(contar("SELECT count(*) FROM movimientos_caja WHERE tipo = 'VENTA' AND referencia_tipo = 'PAGO' AND referencia_id IN (SELECT id FROM pagos WHERE venta_id = ?)", vid))
                .isEqualTo(2);
    }

    @Test
    void prueba13_subtotalesDescuentosEIva() throws Exception {
        // 8 tacos = $10.00; descuento $1.00 => total $9.00 (IVA 13 % incluido)
        long pid = crearPedidoLlevar(List.of(item(taco, 8))).id();
        Map<String, Object> sinMotivo = m("descuento", d("1.00"), "motivoDescuento", "", "pagos", List.of());
        assertThat(cobrar(pid, sinMotivo).mensaje()).contains("motivo del descuento");
        assertThat(cobrar(pid, m("descuento", d("10.01"), "motivoDescuento", "x", "pagos", List.of())).estado()).isEqualTo(400);

        Resp v = cobrar(pid, m("descuento", d("1.00"), "motivoDescuento", "Cliente frecuente", "pagos", List.of(pagoEfectivo("9.00", "10.00"))));
        assertThat(v.dinero("subtotal")).isEqualByComparingTo("10.00");
        assertThat(v.dinero("descuento")).isEqualByComparingTo("1.00");
        assertThat(v.dinero("total")).isEqualByComparingTo("9.00");
        assertThat(v.dinero("ivaPorcentaje")).isEqualByComparingTo("13");
        BigDecimal iva = d("9.00").subtract(d("9.00").divide(d("1.13"), 2, RoundingMode.HALF_UP));
        assertThat(v.dinero("impuesto")).isEqualByComparingTo(iva).isEqualByComparingTo("1.04");
        assertThat(v.dinero("baseImponible")).isEqualByComparingTo("7.96");
        assertThat(v.dinero("baseImponible").add(v.dinero("impuesto"))).isEqualByComparingTo(v.dinero("total"));
        // Los importes de cada linea los calcula el servidor
        JsonNode linea = v.get("detalles").get(0);
        assertThat(linea.get("precioUnitario").decimalValue()).isEqualByComparingTo("1.25");
        assertThat(linea.get("subtotal").decimalValue()).isEqualByComparingTo("10.00");
        // Costo estimado segun receta: 8 x (0.1 x 6.00 + 2 x 0.05) = 5.60
        assertThat(v.dinero("costoEstimado")).isEqualByComparingTo("5.60");
    }

    @Test
    void prueba14_comprobanteInterno() throws Exception {
        Resp venta = cobrar(crearPedidoLlevar(List.of(item(taco, 1))).id(), cobro(pagoEfectivo("1.25", "2.00")));
        Resp c = get("CAJERO", "/api/comprobantes/venta/" + venta.id()).ok();
        JsonNode comp = c.get("comprobante");
        assertThat(comp.get("numero").asText()).matches("CI-\\d{8}");
        assertThat(comp.get("tipo").asText()).isEqualTo("INTERNO");
        assertThat(comp.get("estado").asText()).isEqualTo("VIGENTE");
        assertThat(c.get("leyenda").asText()).contains("COMPROBANTE INTERNO").contains("NO ES UN DOCUMENTO TRIBUTARIO ELECTRÓNICO");
        assertThat(c.get("venta").get("total").decimalValue()).isEqualByComparingTo("1.25");
        assertThat(contar("SELECT count(*) FROM comprobantes WHERE venta_id = ?", venta.id())).isEqualTo(1);
        // Sin integracion con Hacienda no existe ningun documento tributario
        assertThat(contar("SELECT count(*) FROM documentos_tributarios")).isZero();

        // Los datos del negocio quedan fijos en el comprobante aunque luego cambie la configuracion
        JsonNode conf = get("ADMIN", "/api/configuracion").ok().cuerpo();
        String nombreOriginal = conf.get("nombreComercial").asText();
        Map<String, Object> cambio = configuracion(conf, "Nombre cambiado P04");
        put("ADMIN", "/api/configuracion", cambio).ok();
        try {
            JsonNode otra = get("CAJERO", "/api/comprobantes/" + comp.get("id").asLong()).ok().get("comprobante");
            assertThat(otra.get("negocioNombre").asText()).isEqualTo(nombreOriginal);
        } finally {
            put("ADMIN", "/api/configuracion", configuracion(conf, nombreOriginal)).ok();
        }
    }

    private Map<String, Object> configuracion(JsonNode c, String nombre) {
        return m("nombreComercial", nombre, "razonSocial", texto(c, "razonSocial"), "telefono", texto(c, "telefono"),
                "correo", texto(c, "correo"), "direccion", texto(c, "direccion"), "nit", texto(c, "nit"), "nrc", texto(c, "nrc"),
                "giro", texto(c, "giro"), "mensajeComprobante", texto(c, "mensajeComprobante"),
                "ivaPorcentaje", c.get("ivaPorcentaje").decimalValue(), "anchoPapelMm", c.get("anchoPapelMm").asInt());
    }

    private static String texto(JsonNode n, String campo) {
        return n.hasNonNull(campo) ? n.get(campo).asText() : null;
    }

    @Test
    void prueba15_consultaDeComprobantes() throws Exception {
        Resp venta = cobrar(crearPedidoLlevar(List.of(item(horchata, 1))).id(), cobro(pagoEfectivo("1.00", null)));
        String numero = venta.get("comprobanteNumero").asText();
        Resp r = get("CAJERO", "/api/comprobantes?numero=" + numero).ok();
        assertThat(r.get("contenido")).hasSize(1);
        JsonNode fila = r.get("contenido").get(0);
        assertThat(fila.get("numero").asText()).isEqualTo(numero);
        assertThat(fila.get("ventaNumero").asText()).isEqualTo(venta.get("numero").asText());
        assertThat(fila.get("total").decimalValue()).isEqualByComparingTo("1.00");
        // Busqueda por la venta y por fecha de hoy
        assertThat(get("CAJERO", "/api/comprobantes?numero=" + venta.get("numero").asText()).ok().get("contenido")).hasSize(1);
        assertThat(get("CAJERO", "/api/comprobantes?desde=" + java.time.LocalDate.now(java.time.ZoneId.of("America/El_Salvador"))).ok()
                .get("totalElementos").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(get("CAJERO", "/api/comprobantes?numero=CI-99999999").ok().get("contenido")).isEmpty();
        // Un mesero no consulta comprobantes
        assertThat(get("MESERO", "/api/comprobantes").estado()).isEqualTo(403);
    }

    @Test
    void prueba16_reimpresionSinDuplicar() throws Exception {
        Resp venta = cobrar(crearPedidoLlevar(List.of(item(taco, 1))).id(), cobro(pagoEfectivo("1.25", null)));
        long cid = venta.get("comprobanteId").asLong();
        long ventas = contar("SELECT count(*) FROM ventas");
        long comprobantes = contar("SELECT count(*) FROM comprobantes");

        assertThat(post("CAJERO", "/api/comprobantes/" + cid + "/impresiones", null).ok().get("impresiones").asInt()).isEqualTo(1);
        assertThat(post("CAJERO", "/api/comprobantes/" + cid + "/impresiones", null).ok().get("impresiones").asInt()).isEqualTo(2);
        assertThat(post("CAJERO", "/api/comprobantes/" + cid + "/impresiones", null).ok().get("impresiones").asInt()).isEqualTo(3);

        assertThat(contar("SELECT count(*) FROM ventas")).isEqualTo(ventas);
        assertThat(contar("SELECT count(*) FROM comprobantes")).isEqualTo(comprobantes);
        JsonNode comp = get("CAJERO", "/api/comprobantes/" + cid).ok().get("comprobante");
        assertThat(comp.get("impresiones").asInt()).isEqualTo(3);
        assertThat(comp.get("ultimaImpresion").isNull()).isFalse();
        assertThat(post("MESERO", "/api/comprobantes/" + cid + "/impresiones", null).estado()).isEqualTo(403);
    }

    @Test
    void prueba17_descuentoDeInventarioPorReceta() throws Exception {
        Resp pedido = crearPedidoLlevar(List.of(item(taco, 3), item(horchata, 1)));
        assertThat(existencia(carne)).as("el pedido no descuenta").isEqualByComparingTo("10");
        Resp venta = cobrar(pedido.id(), cobro(pagoEfectivo("4.75", null)));
        // 3 tacos x 100 g = 0.3 kg de carne; 3 x 2 = 6 tortillas
        assertThat(existencia(carne)).isEqualByComparingTo("9.7");
        assertThat(existencia(tortilla)).isEqualByComparingTo("494");
        assertThat(contar("SELECT count(*) FROM movimientos_inventario WHERE tipo = 'VENTA' AND referencia_tipo = 'VENTA' AND referencia_id = ?",
                venta.id())).isEqualTo(2);
        // La horchata no tiene receta: se avisa y no se descuenta nada por ella
        assertThat(venta.get("productosSinReceta").toString()).contains("Horchata");

        // Si al cobrar ya no hay existencias, no se registra nada (ni venta, ni pago, ni movimientos)
        long otro = crearPedidoLlevar(List.of(item(taco, 5))).id();
        post("ADMIN", "/api/inventario/movimientos", m("itemId", carne, "tipo", "AJUSTE", "cantidad", d("0.2"), "motivo", "Conteo")).ok();
        long pagos = contar("SELECT count(*) FROM pagos");
        Resp sinExistencia = cobrar(otro, cobro(pagoEfectivo("6.25", null)));
        assertThat(sinExistencia.estado()).isEqualTo(409);
        assertThat(sinExistencia.mensaje()).contains("Existencia insuficiente");
        assertThat(contar("SELECT count(*) FROM ventas WHERE pedido_id = ?", otro)).isZero();
        assertThat(contar("SELECT count(*) FROM pagos")).isEqualTo(pagos);
        assertThat(existencia(carne)).isEqualByComparingTo("0.2");
        assertThat(existencia(tortilla)).isEqualByComparingTo("494");
    }

    @Test
    void prueba18_anulacionYDevolucion() throws Exception {
        Resp pedido = crearPedidoLlevar(List.of(item(taco, 2)));
        Resp venta = cobrar(pedido.id(), cobro(pagoEfectivo("2.50", null)));
        long vid = venta.id();
        assertThat(existencia(carne)).isEqualByComparingTo("9.8");

        // Un pedido con venta no se cancela directamente
        assertThat(post("CAJERO", "/api/pedidos/" + pedido.id() + "/cancelar", m("motivo", "x")).estado()).isEqualTo(409);
        // El cajero no puede anular ventas; sin motivo tampoco se acepta
        assertThat(post("CAJERO", "/api/ventas/" + vid + "/anular", m("motivo", "Error", "devolverInventario", true)).estado()).isEqualTo(403);
        assertThat(post("ADMIN", "/api/ventas/" + vid + "/anular", m("motivo", "", "devolverInventario", true)).estado()).isEqualTo(400);

        Resp anulada = post("ADMIN", "/api/ventas/" + vid + "/anular", m("motivo", "Cliente devolvió la orden sin preparar", "devolverInventario", true)).ok();
        assertThat(anulada.get("estado").asText()).isEqualTo("ANULADA");
        assertThat(anulada.get("inventarioDevuelto").asBoolean()).isTrue();
        assertThat(anulada.get("motivoAnulacion").asText()).contains("sin preparar");
        // Se devuelve exactamente lo descontado
        assertThat(existencia(carne)).isEqualByComparingTo("10");
        assertThat(existencia(tortilla)).isEqualByComparingTo("500");
        // El dinero sale de caja como devolucion y el pago queda revertido
        assertThat(jdbc.queryForObject("SELECT sum(monto) FROM movimientos_caja WHERE tipo = 'DEVOLUCION' AND referencia_tipo = 'PAGO' AND referencia_id IN (SELECT id FROM pagos WHERE venta_id = ?)",
                BigDecimal.class, vid)).isEqualByComparingTo("-2.50");
        assertThat(jdbc.queryForObject("SELECT estado FROM pagos WHERE venta_id = ?", String.class, vid)).isEqualTo("REVERTIDO");
        // El comprobante queda anulado (no se borra) y el pedido cancelado
        assertThat(jdbc.queryForObject("SELECT estado FROM comprobantes WHERE venta_id = ?", String.class, vid)).isEqualTo("ANULADO");
        assertThat(get("CAJERO", "/api/pedidos/" + pedido.id()).ok().get("estado").asText()).isEqualTo("CANCELADO");
        // No se anula dos veces
        assertThat(post("ADMIN", "/api/ventas/" + vid + "/anular", m("motivo", "otra vez", "devolverInventario", true)).estado()).isEqualTo(409);
        assertThat(existencia(carne)).isEqualByComparingTo("10");

        // Anulacion sin devolver inventario (producto ya preparado = merma)
        Resp otra = cobrar(crearPedidoLlevar(List.of(item(taco, 1))).id(), cobro());
        post("ADMIN", "/api/ventas/" + otra.id() + "/anular", m("motivo", "Se quemó", "devolverInventario", false)).ok();
        assertThat(existencia(carne)).isEqualByComparingTo("9.9");
        // Quedo registrado en auditoria
        assertThat(contar("SELECT count(*) FROM auditoria WHERE accion = 'Venta anulada' AND entidad_id = ?", vid)).isEqualTo(1);
    }
}
