package com.taqueria;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * Pruebas 22 a 25: reportes, validacion de datos, prevencion de duplicados y formato de impresion termica.
 * La impresion en una impresora fisica queda pendiente de validacion real (ver docs/IMPRESORA_TERMICA.md).
 */
@Order(6)
class P06ReportesValidacionDuplicadosTest extends PruebaBase {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/El_Salvador"));

    @Test
    void prueba22_reportes() throws Exception {
        asegurarCajaAbierta();
        long horchata = crearProducto("Horchata P06", "Bebidas", "1.00");
        long pid = crearPedidoLlevar(List.of(item(horchata, 3))).id();
        post("CAJERO", "/api/pedidos/" + pid + "/venta", m("pagos", List.of(pagoEfectivo("3.00", null))), clave()).ok();

        // Los 11 reportes responden con columnas y totales
        JsonNode tipos = get("ADMIN", "/api/reportes").ok().cuerpo();
        assertThat(tipos.size()).isEqualTo(11);
        for (Iterator<String> it = tipos.fieldNames(); it.hasNext(); ) {
            String tipo = it.next();
            Resp r = get("ADMIN", "/api/reportes/" + tipo + "?desde=" + HOY + "&hasta=" + HOY);
            assertThat(r.estado()).as("reporte %s: %s", tipo, r.texto()).isEqualTo(200);
            assertThat(r.get("columnas")).as(tipo).isNotEmpty();
            assertThat(r.get("filas").isArray()).isTrue();
        }

        // El reporte de ventas coincide con la base de datos (ventas no anuladas de hoy)
        Resp ventas = get("ADMIN", "/api/reportes/ventas?desde=" + HOY + "&hasta=" + HOY).ok();
        BigDecimal netasBase = jdbc.queryForObject("""
                SELECT coalesce(sum(total), 0) FROM ventas WHERE estado <> 'ANULADA'
                AND (creado_en AT TIME ZONE 'America/El_Salvador')::date = ?""", BigDecimal.class, HOY);
        assertThat(ventas.get("totales").get("ventas_netas").decimalValue()).isEqualByComparingTo(netasBase);
        assertThat(netasBase).isGreaterThanOrEqualTo(d("3.00"));
        // Productos mas vendidos incluye la horchata con 3 unidades
        assertThat(get("ADMIN", "/api/reportes/productos?desde=" + HOY + "&hasta=" + HOY).ok().get("filas").toString())
                .contains("Horchata P06");
        // Agrupacion por mes
        assertThat(get("ADMIN", "/api/reportes/ventas?desde=" + HOY + "&hasta=" + HOY + "&agrupacion=MES").ok()
                .get("filas").get(0).get("periodo").asText()).isEqualTo(HOY.toString().substring(0, 7));

        // CSV con fechas dd/mm/aaaa
        MvcResult csv = mvc.perform(MockMvcRequestBuilders.get("/api/reportes/ventas/csv?desde=" + HOY + "&hasta=" + HOY)
                .with(user(usuario("ADMIN")))).andReturn();
        assertThat(csv.getResponse().getStatus()).isEqualTo(200);
        assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
        String texto = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String fecha = HOY.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        assertThat(texto).contains("Del " + fecha + " al " + fecha).contains("Ventas netas");

        // Panel principal
        Resp panel = get("ADMIN", "/api/dashboard?desde=" + HOY + "&hasta=" + HOY).ok();
        assertThat(panel.get("ventas").get("ventas_netas").decimalValue()).isEqualByComparingTo(netasBase);
        assertThat(panel.cuerpo().has("topProductos")).isTrue();
        assertThat(panel.cuerpo().has("existenciasBajas")).isTrue();

        // Rangos no validos y permisos
        assertThat(get("ADMIN", "/api/reportes/ventas?desde=" + HOY + "&hasta=" + HOY.minusDays(1)).estado()).isEqualTo(400);
        assertThat(get("ADMIN", "/api/reportes/ventas?desde=" + HOY.minusYears(4) + "&hasta=" + HOY).estado()).isEqualTo(400);
        assertThat(get("ADMIN", "/api/reportes/no-existe").estado()).isEqualTo(404);
        assertThat(get("CAJERO", "/api/reportes/ventas").estado()).isEqualTo(403);
    }

    @Test
    void prueba23_validacionDeDatos() throws Exception {
        // Cuerpo vacio: mensajes por campo, en español
        Resp pedido = post("CAJERO", "/api/pedidos", m("tipo", null, "items", List.of()), clave());
        assertThat(pedido.estado()).isEqualTo(400);
        assertThat(pedido.get("campos").get("items").asText()).isEqualTo("Agregue al menos un producto");
        assertThat(pedido.get("campos").get("tipo").asText()).isEqualTo("Seleccione el tipo de pedido");

        Resp cliente = post("CAJERO", "/api/clientes", m("nombre", " ", "correo", "no-es-correo", "activo", true));
        assertThat(cliente.estado()).isEqualTo(400);
        assertThat(cliente.get("campos").has("nombre")).isTrue();
        assertThat(cliente.get("campos").get("correo").asText()).isEqualTo("Correo no válido");

        // JSON mal formado, valor de lista desconocido y texto donde va un numero
        MvcResult malo = mvc.perform(MockMvcRequestBuilders.post("/api/clientes").with(csrf()).with(user(usuario("CAJERO")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\": ")).andReturn();
        assertThat(malo.getResponse().getStatus()).isEqualTo(400);
        assertThat(post("CAJERO", "/api/pedidos", m("tipo", "VOLANDO", "items", List.of()), clave()).estado()).isEqualTo(400);
        assertThat(post("CAJERO", "/api/caja/movimientos", m("tipo", "INGRESO", "monto", "diez", "concepto", "x")).estado()).isEqualTo(400);
        // Producto inexistente
        assertThat(post("CAJERO", "/api/pedidos", m("tipo", "LLEVAR", "items", List.of(item(999999, 1))), clave()).estado()).isEqualTo(404);
        // Textos demasiado largos
        assertThat(post("CAJERO", "/api/clientes", m("nombre", "x".repeat(151), "activo", true)).estado()).isEqualTo(400);
        // Contraseña nueva muy corta
        assertThat(post("ADMIN", "/api/usuarios", m("username", "corta", "nombre", "Corta", "rolId",
                roles.findByCodigo("MESERO").orElseThrow().getId(), "activo", true, "password", "123")).estado()).isEqualTo(400);
        // Los errores internos no exponen detalles tecnicos
        assertThat(malo.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Exception").doesNotContain("at com.");
    }

    @Test
    void prueba24_prevencionDeDuplicados() throws Exception {
        asegurarCajaAbierta();
        long horchata = crearProducto("Horchata P06b", "Bebidas", "1.00");

        // Doble envio del mismo pedido (misma clave de operacion): un solo pedido
        String clavePedido = clave();
        Map<String, Object> cuerpo = m("tipo", "LLEVAR", "items", List.of(item(horchata, 2)));
        Resp p1 = post("CAJERO", "/api/pedidos", cuerpo, clavePedido).ok();
        Resp p2 = post("CAJERO", "/api/pedidos", cuerpo, clavePedido).ok();
        assertThat(p2.id()).isEqualTo(p1.id());
        assertThat(contar("SELECT count(*) FROM pedidos WHERE id >= ?", p1.id())).isEqualTo(1);

        // Doble clic en Cobrar (misma clave): una venta, un pago, un comprobante
        String claveCobro = clave();
        Map<String, Object> cobro = m("pagos", List.of(pagoEfectivo("2.00", "5.00")));
        Resp v1 = post("CAJERO", "/api/pedidos/" + p1.id() + "/venta", cobro, claveCobro).ok();
        Resp v2 = post("CAJERO", "/api/pedidos/" + p1.id() + "/venta", cobro, claveCobro).ok();
        assertThat(v2.id()).isEqualTo(v1.id());
        // Otro intento de cobrar el mismo pedido (otra clave) se rechaza
        assertThat(post("CAJERO", "/api/pedidos/" + p1.id() + "/venta", cobro, clave()).estado()).isEqualTo(409);
        // Sin clave tambien se rechaza
        assertThat(post("CAJERO", "/api/pedidos/" + p1.id() + "/venta", cobro).estado()).isEqualTo(409);
        assertThat(contar("SELECT count(*) FROM ventas WHERE pedido_id = ?", p1.id())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pagos WHERE venta_id = ?", v1.id())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM comprobantes WHERE venta_id = ?", v1.id())).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM movimientos_caja WHERE referencia_tipo = 'PAGO' AND referencia_id IN (SELECT id FROM pagos WHERE venta_id = ?)", v1.id()))
                .isEqualTo(1);

        // Cobros simultaneos del mismo pedido desde dos cajas: solo uno se registra y el inventario se descuenta una vez
        long tortilla = crearItem("Tortilla P06", "u", "100", "0", "0.05");
        long taco = crearProducto("Taco P06", "Tacos", "1.25");
        receta(taco, tortilla, "2", "u");
        long pid = crearPedidoLlevar(List.of(item(taco, 4))).id();
        int hilos = 6;
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(ejecutor.submit(() -> {
                salida.await();
                return post("CAJERO", "/api/pedidos/" + pid + "/venta", m("pagos", List.of(pagoEfectivo("5.00", null))), clave()).estado();
            }));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> f : resultados) estados.add(f.get(60, TimeUnit.SECONDS));
        ejecutor.shutdown();
        assertThat(estados).filteredOn(e -> e == 201).hasSize(1);
        assertThat(estados).filteredOn(e -> e == 409).hasSize(hilos - 1);
        assertThat(contar("SELECT count(*) FROM ventas WHERE pedido_id = ?", pid)).isEqualTo(1);
        assertThat(existencia(tortilla)).isEqualByComparingTo("92");
        assertThat(contar("SELECT count(*) FROM movimientos_inventario WHERE item_id = ? AND tipo = 'VENTA'", tortilla)).isEqualTo(1);

        // Numero de tarjeta completo en la referencia: rechazado (no se guardan datos de tarjetas)
        long otro = crearPedidoLlevar(List.of(item(horchata, 1))).id();
        Resp tarjeta = post("CAJERO", "/api/pedidos/" + otro + "/venta", m("pagos", List.of(
                m("metodo", "TARJETA", "monto", d("1.00"), "referencia", "4111 1111 1111 1111"))), clave());
        assertThat(tarjeta.estado()).isEqualTo(400);
        assertThat(contar("SELECT count(*) FROM pagos WHERE referencia LIKE '%4111%1111%'")).isZero();

        // Nombres repetidos en catalogos
        assertThat(post("ADMIN", "/api/categorias", m("nombre", "Tacos", "orden", 1, "activa", true)).estado()).isEqualTo(409);
        post("ADMIN", "/api/usuarios", m("username", "duplicado", "nombre", "Uno", "rolId",
                roles.findByCodigo("MESERO").orElseThrow().getId(), "activo", true, "password", "duplicado-1")).ok();
        assertThat(post("ADMIN", "/api/usuarios", m("username", "duplicado", "nombre", "Dos", "rolId",
                roles.findByCodigo("MESERO").orElseThrow().getId(), "activo", true, "password", "duplicado-2")).estado()).isEqualTo(409);
    }

    @Test
    void prueba25_disenoDeImpresionTermica() throws Exception {
        // Estilos de impresion: solo el ticket, sin margenes, anchos de 58 y 80 mm
        String css = mvc.perform(MockMvcRequestBuilders.get("/css/app.css")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(css).contains("@media print").contains("body.pagina-comprobante")
                .contains(".papel-termico.ancho-80 { width: 80mm; }").contains(".papel-termico.ancho-58 { width: 58mm;");
        // El ticket muestra el tipo de documento correcto y la leyenda; nunca "factura"
        String ticket = mvc.perform(MockMvcRequestBuilders.get("/js/comun/ticket.js")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(ticket).contains("COMPROBANTE INTERNO DE VENTA").contains("REIMPRESIÓN").contains("leyenda");
        assertThat(ticket.toLowerCase(Locale.ROOT)).doesNotContain("factura fiscal");
        // Los datos que el ticket necesita llegan completos desde la API
        asegurarCajaAbierta();
        long horchata = crearProducto("Horchata P06c", "Bebidas", "1.00");
        Resp venta = post("CAJERO", "/api/ventas/directa", m("items", List.of(item(horchata, 1)),
                "cobro", m("pagos", List.of(pagoEfectivo("1.00", "5.00")))), clave()).ok();
        Resp vista = get("CAJERO", "/api/comprobantes/" + venta.get("comprobanteId").asLong()).ok();
        assertThat(vista.get("anchoPapelMm").asInt()).isIn(58, 80);
        assertThat(vista.get("leyenda").asText()).contains("NO ES UN DOCUMENTO TRIBUTARIO");
        assertThat(vista.get("venta").get("pagos").get(0).get("cambio").decimalValue()).isEqualByComparingTo("4.00");
        // La impresion en papel termico real se verifica con pruebas-navegador/impresion.js (PDF de una pagina
        // de 58/80 mm). La prueba en una impresora fisica queda PENDIENTE DE VALIDACION REAL.
    }
}
