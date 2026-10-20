package com.taqueria;

import com.fasterxml.jackson.databind.JsonNode;
import com.taqueria.service.PedidoEventos;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas 7 a 10: pedidos con varios productos, persistencia, panel de cocina y estados. */
@Order(3)
@RecordApplicationEvents
class P03PedidosCocinaTest extends PruebaBase {

    @Autowired ApplicationEvents eventos;

    /** Taco con receta (100 g de carne y 2 tortillas) y una bebida sin receta. */
    private long[] menu(String sufijo) throws Exception {
        long carne = crearItem("Carne " + sufijo, "kg", "5", "1", "6.00");
        long tortilla = crearItem("Tortilla " + sufijo, "u", "200", "10", "0.05");
        long taco = crearProducto("Taco " + sufijo, "Tacos", "1.25");
        receta(taco, carne, "100", "g", tortilla, "2", "u");
        long horchata = crearProducto("Horchata " + sufijo, "Bebidas", "1.00");
        long queso = post("ADMIN", "/api/complementos", m("nombre", "Queso extra " + sufijo, "precioExtra", d("0.50"),
                "categoriaId", null, "activo", true)).ok().id();
        return new long[]{carne, tortilla, taco, horchata, queso};
    }

    @Test
    void prueba07_pedidoConVariosProductos() throws Exception {
        long[] x = menu("P03a");
        long mesa = post("ADMIN", "/api/mesas", m("nombre", "Mesa P03", "capacidad", 4, "activa", true)).ok().id();

        Resp p = post("MESERO", "/api/pedidos", m("tipo", "LOCAL", "mesaId", mesa, "notas", "Cliente con prisa", "items", List.of(
                m("productoId", x[2], "cantidad", 3, "observaciones", "sin cebolla", "complementoIds", List.of(x[4])),
                m("productoId", x[3], "cantidad", 2, "observaciones", null, "complementoIds", List.of()))), clave());
        assertThat(p.estado()).isEqualTo(201);
        assertThat(p.get("numero").asText()).startsWith("P-");
        assertThat(p.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(p.get("mesa").asText()).isEqualTo("Mesa P03");
        JsonNode det = p.get("detalles");
        assertThat(det).hasSize(2);
        assertThat(det.get(0).get("observaciones").asText()).isEqualTo("sin cebolla");
        assertThat(det.get(0).get("complementos").get(0).get("nombre").asText()).isEqualTo("Queso extra P03a");
        // Precio unitario = 1.25 + 0.50 de complemento; total = 3 x 1.75 + 2 x 1.00
        assertThat(det.get(0).get("precioUnitario").decimalValue()).isEqualByComparingTo("1.75");
        assertThat(p.dinero("total")).isEqualByComparingTo("7.25");

        // Tomar el pedido no descuenta inventario (se descuenta al confirmar la venta)
        assertThat(existencia(x[0])).isEqualByComparingTo("5");

        // Reglas por tipo de pedido y cantidades
        assertThat(post("MESERO", "/api/pedidos", m("tipo", "LOCAL", "items", List.of(item(x[3], 1))), clave()).mensaje())
                .contains("Seleccione la mesa");
        assertThat(post("MESERO", "/api/pedidos", m("tipo", "DOMICILIO", "items", List.of(item(x[3], 1))), clave()).estado())
                .isEqualTo(400);
        assertThat(post("MESERO", "/api/pedidos", m("tipo", "LLEVAR", "items", List.of(item(x[3], 0))), clave()).estado())
                .isEqualTo(400);
        assertThat(post("MESERO", "/api/pedidos", m("tipo", "LLEVAR", "items", List.of()), clave()).estado()).isEqualTo(400);
        // Sin existencias suficientes para 100 tacos (10 kg de carne, hay 5) el pedido no se registra
        Resp sinExistencia = post("MESERO", "/api/pedidos", m("tipo", "LLEVAR", "items", List.of(item(x[2], 100))), clave());
        assertThat(sinExistencia.estado()).isEqualTo(409);
        assertThat(sinExistencia.mensaje()).contains("No hay existencias suficientes");
        // Domicilio con datos completos si se acepta
        assertThat(post("MESERO", "/api/pedidos", m("tipo", "DOMICILIO", "clienteNombre", "Cliente demo", "clienteTelefono", "0000-0000",
                "direccionEntrega", "Dirección de prueba", "items", List.of(item(x[3], 1))), clave()).estado()).isEqualTo(201);
    }

    @Test
    void prueba08_persistenciaDelPedido() throws Exception {
        long[] x = menu("P03b");
        Resp p = post("CAJERO", "/api/pedidos", m("tipo", "LLEVAR", "clienteNombre", "Ana (dato de prueba)", "items", List.of(
                m("productoId", x[2], "cantidad", 2, "observaciones", "bien dorado", "complementoIds", List.of(x[4])))), clave()).ok();
        long id = p.id();

        // Se lee de nuevo desde la base: mismos datos, observaciones y complementos
        Resp leido = get("CAJERO", "/api/pedidos/" + id).ok();
        assertThat(leido.get("numero").asText()).isEqualTo(p.get("numero").asText());
        assertThat(leido.get("clienteNombre").asText()).isEqualTo("Ana (dato de prueba)");
        assertThat(leido.get("detalles").get(0).get("observaciones").asText()).isEqualTo("bien dorado");
        assertThat(contar("SELECT count(*) FROM pedido_detalles WHERE pedido_id = ?", id)).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pedido_detalle_complementos c JOIN pedido_detalles d ON d.id = c.detalle_id WHERE d.pedido_id = ?", id))
                .isEqualTo(1);

        // Cambiar el precio del producto no altera el pedido ya registrado (precio historico)
        put("ADMIN", "/api/productos/" + x[2], m("nombre", "Taco P03b", "categoriaId", categoria("Tacos"), "tipo", "PREPARADO",
                "precio", d("2.00"), "costoEstimado", d("0"), "activo", true, "disponible", true)).ok();
        assertThat(get("CAJERO", "/api/pedidos/" + id).ok().dinero("total")).isEqualByComparingTo("3.50");

        // Se encuentra por numero
        Resp busqueda = get("CAJERO", "/api/pedidos?numero=" + p.get("numero").asText()).ok();
        assertThat(busqueda.get("contenido")).hasSize(1);
        assertThat(busqueda.get("contenido").get(0).get("id").asLong()).isEqualTo(id);
    }

    @Test
    void prueba09_panelDeCocina() throws Exception {
        long[] x = menu("P03c");
        eventos.clear();
        Resp p = post("MESERO", "/api/pedidos", m("tipo", "LLEVAR", "items", List.of(
                m("productoId", x[2], "cantidad", 1, "observaciones", "sin picante", "complementoIds", List.of()))), clave()).ok();
        long id = p.id();

        // El registro avisa a las pantallas conectadas (la cocina lo recibe por eventos del servidor)
        assertThat(eventos.stream(PedidoEventos.PedidoCambiado.class).filter(e -> e.id().equals(id))).isNotEmpty();

        // La cocina ve el pedido con sus observaciones
        JsonNode enCocina = buscar(get("COCINA", "/api/pedidos/cocina").ok().cuerpo(), id);
        assertThat(enCocina).isNotNull();
        assertThat(enCocina.get("detalles").get(0).get("observaciones").asText()).isEqualTo("sin picante");

        // La cocina actualiza el estado y se vuelve a avisar
        eventos.clear();
        put("COCINA", "/api/pedidos/" + id + "/estado", m("estado", "EN_PREPARACION")).ok();
        assertThat(eventos.stream(PedidoEventos.PedidoCambiado.class).filter(e -> e.id().equals(id))).isNotEmpty();

        // Entregado ya no aparece en cocina
        put("COCINA", "/api/pedidos/" + id + "/estado", m("estado", "LISTO")).ok();
        put("CAJERO", "/api/pedidos/" + id + "/estado", m("estado", "ENTREGADO")).ok();
        assertThat(buscar(get("COCINA", "/api/pedidos/cocina").ok().cuerpo(), id)).isNull();
        // La cocina no puede cobrar ni crear pedidos
        assertThat(post("COCINA", "/api/pedidos/" + id + "/venta", m("pagos", List.of()), clave()).estado()).isEqualTo(403);
        assertThat(post("COCINA", "/api/pedidos", m("tipo", "LLEVAR", "items", List.of(item(x[3], 1))), clave()).estado()).isEqualTo(403);
    }

    private static JsonNode buscar(JsonNode lista, long id) {
        return StreamSupport.stream(lista.spliterator(), false).filter(n -> n.get("id").asLong() == id).findFirst().orElse(null);
    }

    @Test
    void prueba10_estadosDelPedido() throws Exception {
        long[] x = menu("P03d");
        long id = crearPedidoLlevar(List.of(item(x[3], 1))).id();
        String url = "/api/pedidos/" + id + "/estado";

        assertThat(put("COCINA", url, m("estado", "ENTREGADO")).estado()).as("de pendiente no se entrega directo").isEqualTo(409);
        put("COCINA", url, m("estado", "EN_PREPARACION")).ok();
        put("COCINA", url, m("estado", "PENDIENTE", "motivo", "Se tomó por error")).ok();
        put("COCINA", url, m("estado", "EN_PREPARACION")).ok();
        put("COCINA", url, m("estado", "LISTO")).ok();
        Resp entregado = put("MESERO", url, m("estado", "ENTREGADO")).ok();
        assertThat(entregado.get("estado").asText()).isEqualTo("ENTREGADO");
        assertThat(entregado.get("siguientesEstados")).isEmpty();
        assertThat(put("COCINA", url, m("estado", "LISTO")).estado()).as("entregado es final").isEqualTo(409);
        assertThat(put("COCINA", url, m("estado", "CANCELADO")).estado()).as("cancelar exige motivo").isEqualTo(400);

        // Historial completo con usuario y motivo
        JsonNode historial = get("CAJERO", "/api/pedidos/" + id + "/historial").ok().cuerpo();
        assertThat(historial).hasSize(6);
        assertThat(StreamSupport.stream(historial.spliterator(), false).map(h -> h.get("estadoNuevo").asText()).toList())
                .containsExactly("PENDIENTE", "EN_PREPARACION", "PENDIENTE", "EN_PREPARACION", "LISTO", "ENTREGADO");
        assertThat(historial.get(2).get("motivo").asText()).isEqualTo("Se tomó por error");
        assertThat(historial.get(5).get("usuario").asText()).isEqualTo("Prueba MESERO");

        // Cancelacion: necesita motivo y permiso; un pedido entregado no se cancela
        assertThat(post("CAJERO", "/api/pedidos/" + id + "/cancelar", m("motivo", "x")).estado()).isEqualTo(409);
        long otro = crearPedidoLlevar(List.of(item(x[3], 1))).id();
        assertThat(post("COCINA", "/api/pedidos/" + otro + "/cancelar", m("motivo", "Cliente se fue")).estado()).isEqualTo(403);
        assertThat(post("CAJERO", "/api/pedidos/" + otro + "/cancelar", m("motivo", "")).estado()).isEqualTo(400);
        assertThat(post("CAJERO", "/api/pedidos/" + otro + "/cancelar", m("motivo", "Cliente se fue")).ok().get("estado").asText())
                .isEqualTo("CANCELADO");
        Map<String, Object> ultimo = jdbc.queryForMap("SELECT estado_nuevo, motivo FROM pedido_estados WHERE pedido_id = ? ORDER BY id DESC LIMIT 1", otro);
        assertThat(ultimo.get("estado_nuevo")).isEqualTo("CANCELADO");
        assertThat(ultimo.get("motivo")).isEqualTo("Cliente se fue");
    }
}
