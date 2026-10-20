package com.taqueria;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas 19 a 21: apertura y cierre de caja, gastos, y compras que ingresan al inventario. */
@Order(5)
@TestMethodOrder(MethodOrderer.MethodName.class)
class P05CajaGastosComprasTest extends PruebaBase {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/El_Salvador"));

    private void cerrarSiEstaAbierta() throws Exception {
        Resp actual = get("CAJERO", "/api/caja/actual").ok();
        if (actual.get("abierta").asBoolean()) {
            post("CAJERO", "/api/caja/cerrar", m("efectivoContado", actual.get("resumen").get("efectivoEsperado").decimalValue())).ok();
        }
    }

    private BigDecimal efectivoEsperado() throws Exception {
        return get("CAJERO", "/api/caja/actual").ok().get("resumen").get("efectivoEsperado").decimalValue();
    }

    @Test
    void prueba19_aperturaYCierreDeCaja() throws Exception {
        long horchata = crearProducto("Horchata P05", "Bebidas", "1.00");
        cerrarSiEstaAbierta();

        // Con la caja cerrada no se puede cobrar: no se registra la venta
        long pid = crearPedidoLlevar(List.of(item(horchata, 10))).id();
        Resp sinCaja = post("CAJERO", "/api/pedidos/" + pid + "/venta", m("pagos", List.of(pagoEfectivo("10.00", null))), clave());
        assertThat(sinCaja.estado()).isEqualTo(409);
        assertThat(sinCaja.mensaje()).contains("caja está cerrada");
        assertThat(contar("SELECT count(*) FROM ventas WHERE pedido_id = ?", pid)).isZero();
        assertThat(post("CAJERO", "/api/caja/cerrar", m("efectivoContado", d("0"))).estado()).isEqualTo(409);
        assertThat(post("MESERO", "/api/caja/abrir", m("montoInicial", d("100"))).estado()).isEqualTo(403);

        // Apertura con fondo inicial; no se puede abrir otra
        Resp abierta = post("CAJERO", "/api/caja/abrir", m("montoInicial", d("100.00"), "notas", "Turno de prueba"));
        assertThat(abierta.estado()).isEqualTo(201);
        long cajaId = abierta.get("caja").get("id").asLong();
        assertThat(post("CAJERO", "/api/caja/abrir", m("montoInicial", d("10"))).estado()).isEqualTo(409);

        // Movimientos del turno: efectivo $10, tarjeta $5, una venta pendiente (no cuenta), entrada $20 y retiro $15
        post("CAJERO", "/api/pedidos/" + pid + "/venta", m("pagos", List.of(pagoEfectivo("10.00", "20.00"))), clave()).ok();
        post("CAJERO", "/api/ventas/directa", m("items", List.of(item(horchata, 5)),
                "cobro", m("pagos", List.of(m("metodo", "TARJETA", "monto", d("5.00"), "referencia", "Aut. 4455")))), clave()).ok();
        post("CAJERO", "/api/ventas/directa", m("items", List.of(item(horchata, 3)), "cobro", m("pagos", List.of())), clave()).ok();
        post("CAJERO", "/api/caja/movimientos", m("tipo", "INGRESO", "monto", d("20.00"), "concepto", "Cambio adicional")).ok();
        post("CAJERO", "/api/caja/movimientos", m("tipo", "RETIRO", "monto", d("15.00"), "concepto", "Depósito al banco")).ok();
        assertThat(post("CAJERO", "/api/caja/movimientos", m("tipo", "RETIRO", "monto", d("1000.00"), "concepto", "x")).estado())
                .as("no se retira mas efectivo del que hay").isEqualTo(409);
        assertThat(post("CAJERO", "/api/caja/movimientos", m("tipo", "VENTA", "monto", d("1.00"), "concepto", "x")).estado()).isEqualTo(400);

        JsonNode resumen = get("CAJERO", "/api/caja/actual").ok().get("resumen");
        assertThat(resumen.get("ventasEfectivo").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(resumen.get("ventasTarjeta").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(resumen.get("ingresos").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(resumen.get("retiros").decimalValue()).isEqualByComparingTo("-15.00");
        // Esperado = 100 + 10 (efectivo) + 20 - 15; la tarjeta y la venta pendiente no suman al efectivo
        assertThat(resumen.get("efectivoEsperado").decimalValue()).isEqualByComparingTo("115.00");

        // Cierre con faltante de $2
        Resp cierre = post("CAJERO", "/api/caja/cerrar", m("efectivoContado", d("113.00"), "notas", "Faltante en monedas")).ok();
        JsonNode caja = cierre.get("caja");
        assertThat(caja.get("estado").asText()).isEqualTo("CERRADA");
        assertThat(caja.get("efectivoEsperado").decimalValue()).isEqualByComparingTo("115.00");
        assertThat(caja.get("efectivoContado").decimalValue()).isEqualByComparingTo("113.00");
        assertThat(caja.get("diferencia").decimalValue()).isEqualByComparingTo("-2.00");
        assertThat(cierre.get("cerradaPor").asText()).isEqualTo("Prueba CAJERO");
        assertThat(get("CAJERO", "/api/caja/actual").ok().get("abierta").asBoolean()).isFalse();
        // El cierre queda en el historial
        assertThat(get("CAJERO", "/api/caja/" + cajaId).ok().get("caja").get("diferencia").decimalValue()).isEqualByComparingTo("-2.00");
        assertThat(contar("SELECT count(*) FROM auditoria WHERE accion = 'Cierre de caja' AND entidad_id = ?", cajaId)).isEqualTo(1);
    }

    @Test
    void prueba20_gastos() throws Exception {
        asegurarCajaAbierta();
        long gas = jdbc.queryForObject("SELECT id FROM categorias_gasto WHERE nombre = 'Gas'", Long.class);
        BigDecimal antes = efectivoEsperado();

        Resp gasto = post("CAJERO", "/api/gastos", m("fecha", HOY.toString(), "categoriaId", gas, "concepto", "Cilindro de gas",
                "monto", d("12.50"), "metodoPago", "EFECTIVO", "pagadoDesdeCaja", true, "numeroComprobante", "F-001"));
        assertThat(gasto.estado()).isEqualTo(201);
        assertThat(efectivoEsperado()).isEqualByComparingTo(antes.subtract(d("12.50")));
        // Gasto con transferencia no toca la caja
        post("CAJERO", "/api/gastos", m("fecha", HOY.toString(), "categoriaId", gas, "concepto", "Pago de gas por banco",
                "monto", d("30.00"), "metodoPago", "TRANSFERENCIA", "pagadoDesdeCaja", false)).ok();
        assertThat(efectivoEsperado()).isEqualByComparingTo(antes.subtract(d("12.50")));

        // Validaciones
        assertThat(post("CAJERO", "/api/gastos", m("fecha", HOY.plusDays(1).toString(), "categoriaId", gas, "concepto", "Futuro",
                "monto", d("1"), "metodoPago", "EFECTIVO", "pagadoDesdeCaja", false)).estado()).isEqualTo(400);
        assertThat(post("CAJERO", "/api/gastos", m("fecha", HOY.toString(), "categoriaId", gas, "concepto", "Tarjeta desde caja",
                "monto", d("1"), "metodoPago", "TARJETA", "pagadoDesdeCaja", true)).estado()).isEqualTo(400);
        Resp sinMonto = post("CAJERO", "/api/gastos", m("fecha", HOY.toString(), "categoriaId", gas, "concepto", "",
                "monto", d("0"), "metodoPago", "EFECTIVO", "pagadoDesdeCaja", false));
        assertThat(sinMonto.estado()).isEqualTo(400);
        assertThat(sinMonto.get("campos").has("monto")).isTrue();
        assertThat(sinMonto.get("campos").has("concepto")).isTrue();

        // Consulta del periodo
        JsonNode lista = get("CAJERO", "/api/gastos?desde=" + HOY + "&hasta=" + HOY).ok().cuerpo();
        assertThat(lista.toString()).contains("Cilindro de gas").contains("Pago de gas por banco");

        // Anular devuelve el efectivo a la caja y no se puede anular dos veces
        Resp anulado = post("CAJERO", "/api/gastos/" + gasto.id() + "/anular", m("motivo", "Registrado por error")).ok();
        assertThat(anulado.get("anulado").asBoolean()).isTrue();
        assertThat(efectivoEsperado()).isEqualByComparingTo(antes);
        assertThat(post("CAJERO", "/api/gastos/" + gasto.id() + "/anular", m("motivo", "x")).estado()).isEqualTo(409);
        assertThat(post("MESERO", "/api/gastos", m("fecha", HOY.toString(), "categoriaId", gas, "concepto", "x",
                "monto", d("1"), "metodoPago", "EFECTIVO", "pagadoDesdeCaja", false)).estado()).isEqualTo(403);
    }

    @Test
    void prueba21_comprasEInventario() throws Exception {
        long proveedor = post("ADMIN", "/api/proveedores", m("nombreComercial", "Proveedor de prueba P05", "telefono", "0000-0000",
                "activo", true)).ok().id();
        long carne = crearItem("Carne compra P05", "kg", "2", "1", "6.00");
        long tortilla = crearItem("Tortilla compra P05", "u", "0", "10", "0.05");
        var detalles = List.of(m("itemId", carne, "cantidad", d("8"), "costoUnitario", d("6.25"), "lote", "L-01",
                        "fechaVencimiento", HOY.plusDays(5).toString()),
                m("itemId", tortilla, "cantidad", d("300"), "costoUnitario", d("0.04")));

        assertThat(post("INVENTARIO", "/api/compras", m("proveedorId", proveedor, "fecha", HOY.toString(), "detalles", detalles)).estado())
                .as("el encargado de inventario recibe, pero no registra compras").isEqualTo(403);
        Resp compra = post("ADMIN", "/api/compras", m("proveedorId", proveedor, "fecha", HOY.toString(), "numeroFactura", "FAC-PRUEBA-1",
                "fechaVencimientoPago", HOY.plusDays(15).toString(), "recibirAhora", false, "detalles", detalles));
        assertThat(compra.estado()).isEqualTo(201);
        long id = compra.id();
        // Total = 8 x 6.25 + 300 x 0.04 = 62.00; sin recibir no cambia el inventario
        assertThat(compra.dinero("total")).isEqualByComparingTo("62.00");
        assertThat(compra.get("estado").asText()).isEqualTo("PENDIENTE_RECEPCION");
        assertThat(existencia(carne)).isEqualByComparingTo("2");

        // Recepcion: suma existencias, actualiza costo y registra movimientos COMPRA
        post("INVENTARIO", "/api/compras/" + id + "/recibir", null).ok();
        assertThat(existencia(carne)).isEqualByComparingTo("10");
        assertThat(existencia(tortilla)).isEqualByComparingTo("300");
        assertThat(jdbc.queryForObject("SELECT costo_unitario FROM inventario_items WHERE id = ?", BigDecimal.class, carne))
                .isEqualByComparingTo("6.25");
        assertThat(contar("SELECT count(*) FROM movimientos_inventario WHERE tipo = 'COMPRA' AND referencia_id = ?", id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT lote FROM inventario_items WHERE id = ?", String.class, carne)).isEqualTo("L-01");

        // No se puede recibir dos veces ni anular una compra ya recibida
        Resp otraVez = post("INVENTARIO", "/api/compras/" + id + "/recibir", null);
        assertThat(otraVez.estado()).isEqualTo(409);
        assertThat(existencia(carne)).isEqualByComparingTo("10");
        assertThat(post("ADMIN", "/api/compras/" + id + "/anular", m("motivo", "x")).estado()).isEqualTo(409);

        // Pago parcial, deuda pendiente y pago mayor al saldo
        Resp pagada = post("ADMIN", "/api/compras/" + id + "/pagos", m("monto", d("40.00"), "metodo", "TRANSFERENCIA",
                "fecha", HOY.toString(), "desdeCaja", false)).ok();
        assertThat(pagada.dinero("montoPagado")).isEqualByComparingTo("40.00");
        assertThat(post("ADMIN", "/api/compras/" + id + "/pagos", m("monto", d("30.00"), "metodo", "TRANSFERENCIA", "desdeCaja", false))
                .estado()).isEqualTo(400);
        JsonNode deudas = get("ADMIN", "/api/compras/deudas").ok().cuerpo();
        assertThat(deudas.toString()).contains("Proveedor de prueba P05");

        // Una compra pendiente si se puede anular y no toca el inventario
        long otra = post("ADMIN", "/api/compras", m("proveedorId", proveedor, "fecha", HOY.toString(), "recibirAhora", false,
                "detalles", List.of(m("itemId", carne, "cantidad", d("1"), "costoUnitario", d("6"))))).ok().id();
        assertThat(post("ADMIN", "/api/compras/" + otra + "/anular", m("motivo", "Pedido duplicado")).ok().get("estado").asText())
                .isEqualTo("ANULADA");
        assertThat(post("INVENTARIO", "/api/compras/" + otra + "/recibir", null).estado()).isEqualTo(409);
        assertThat(existencia(carne)).isEqualByComparingTo("10");

        // Compra recibida al momento de registrarla
        post("ADMIN", "/api/compras", m("proveedorId", proveedor, "fecha", HOY.toString(), "recibirAhora", true,
                "detalles", List.of(m("itemId", tortilla, "cantidad", d("50"), "costoUnitario", d("0.04"))))).ok();
        assertThat(existencia(tortilla)).isEqualByComparingTo("350");
    }
}
