package com.taqueria;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas 3 a 6: productos, ingredientes, recetas y movimientos manuales de inventario. */
@Order(2)
class P02CatalogoInventarioTest extends PruebaBase {

    private Map<String, Object> producto(String codigo, String nombre, Object precio) {
        return m("codigo", codigo, "nombre", nombre, "categoriaId", categoria("Tacos"), "tipo", "PREPARADO",
                "precio", precio, "costoEstimado", d("0"), "activo", true, "disponible", true);
    }

    @Test
    void prueba03_productos() throws Exception {
        Resp creado = post("ADMIN", "/api/productos", producto("P02-1", "Taco al pastor P02", d("1.25")));
        assertThat(creado.estado()).isEqualTo(201);
        long id = creado.id();
        assertThat(creado.dinero("precio")).isEqualByComparingTo("1.25");

        // Validaciones del servidor: precio negativo, mas de dos decimales y codigo repetido
        Resp negativo = post("ADMIN", "/api/productos", producto("P02-2", "Negativo", d("-1")));
        assertThat(negativo.estado()).isEqualTo(400);
        assertThat(negativo.get("campos").has("precio")).isTrue();
        assertThat(post("ADMIN", "/api/productos", producto("P02-3", "Tres decimales", d("1.255"))).estado()).isEqualTo(400);
        assertThat(post("ADMIN", "/api/productos", producto("P02-1", "Repetido", d("1"))).estado()).isEqualTo(409);

        // Editar el precio
        put("ADMIN", "/api/productos/" + id, producto("P02-1", "Taco al pastor P02", d("1.50"))).ok();
        assertThat(get("CAJERO", "/api/productos/" + id).ok().dinero("precio")).isEqualByComparingTo("1.50");

        // Desactivar conserva el registro (para el historial) pero ya no se puede pedir
        assertThat(post("ADMIN", "/api/productos/" + id + "/desactivar", null).ok().get("activo").asBoolean()).isFalse();
        assertThat(contar("SELECT count(*) FROM productos WHERE id = ?", id)).isEqualTo(1);
        asegurarCajaAbierta();
        Resp pedido = post("CAJERO", "/api/pedidos", m("tipo", "LLEVAR", "items", List.of(item(id, 1))), clave());
        assertThat(pedido.estado()).isEqualTo(400);
        assertThat(pedido.mensaje()).contains("no está disponible");
    }

    @Test
    void prueba04_ingredientes() throws Exception {
        Resp creado = post("ADMIN", "/api/inventario", m("codigo", "ING-P02", "nombre", "Carne de res P02", "tipo", "INGREDIENTE",
                "categoria", "Carnes", "unidadId", unidad("kg"), "existenciaInicial", d("10"), "existenciaMinima", d("2"),
                "costoUnitario", d("6.50"), "activo", true));
        assertThat(creado.estado()).isEqualTo(201);
        long id = creado.id();
        assertThat(existencia(id)).isEqualByComparingTo("10");
        // La existencia inicial queda registrada como movimiento
        assertThat(contar("SELECT count(*) FROM movimientos_inventario WHERE item_id = ? AND tipo = 'ENTRADA'", id)).isEqualTo(1);

        assertThat(post("ADMIN", "/api/inventario", m("codigo", "ING-P02", "nombre", "Otro", "tipo", "INGREDIENTE",
                "categoria", "Carnes", "unidadId", unidad("kg"), "existenciaMinima", d("0"), "costoUnitario", d("1"),
                "activo", true)).estado()).isEqualTo(409);
        Resp sinUnidad = post("ADMIN", "/api/inventario", m("nombre", "Sin unidad", "tipo", "INGREDIENTE", "categoria", "X",
                "existenciaMinima", d("0"), "costoUnitario", d("1"), "activo", true));
        assertThat(sinUnidad.estado()).isEqualTo(400);
        assertThat(sinUnidad.get("campos").has("unidadId")).isTrue();

        // Editar no cambia la existencia (eso solo se hace con movimientos)
        put("ADMIN", "/api/inventario/" + id, m("codigo", "ING-P02", "nombre", "Carne de res P02", "tipo", "INGREDIENTE",
                "categoria", "Carnes", "unidadId", unidad("kg"), "existenciaInicial", d("999"), "existenciaMinima", d("3"),
                "costoUnitario", d("6.50"), "activo", true)).ok();
        assertThat(existencia(id)).isEqualByComparingTo("10");
        // No se puede cambiar la unidad de un articulo con existencias
        assertThat(put("ADMIN", "/api/inventario/" + id, m("codigo", "ING-P02", "nombre", "Carne de res P02", "tipo", "INGREDIENTE",
                "categoria", "Carnes", "unidadId", unidad("l"), "existenciaMinima", d("3"), "costoUnitario", d("6.50"),
                "activo", true)).estado()).isEqualTo(400);
    }

    @Test
    void prueba05_recetas() throws Exception {
        long carne = crearItem("Carne receta P02", "kg", "5", "1", "6.50");
        long tortilla = crearItem("Tortilla receta P02", "u", "100", "10", "0.05");
        long taco = crearProducto("Taco receta P02", "Tacos", "1.25");

        // Sin receta el producto aparece como "sin configurar"
        assertThat(fila(taco).get("descuentoAutomatico").asText()).isEqualTo("SIN_CONFIGURAR");

        // 100 g de carne (el articulo se maneja en kg) y 2 tortillas
        receta(taco, carne, "100", "g", tortilla, "2", "u");
        JsonNode f = fila(taco);
        assertThat(f.get("descuentoAutomatico").asText()).isEqualTo("RECETA");
        assertThat(f.get("ingredientes").asInt()).isEqualTo(2);
        // Costo: 0.1 kg x $6.50 + 2 x $0.05 = $0.75
        assertThat(f.get("costoReceta").decimalValue()).isEqualByComparingTo("0.75");

        // Unidad incompatible (litros para un articulo en kg) e ingrediente repetido se rechazan
        Resp incompatible = put("ADMIN", "/api/recetas/" + taco, m("detalles", List.of(
                m("itemId", carne, "cantidad", d("1"), "unidadId", unidad("l")))));
        assertThat(incompatible.estado()).isEqualTo(400);
        assertThat(incompatible.mensaje()).contains("no es compatible");
        assertThat(put("ADMIN", "/api/recetas/" + taco, m("detalles", List.of(
                m("itemId", carne, "cantidad", d("1"), "unidadId", unidad("g")),
                m("itemId", carne, "cantidad", d("2"), "unidadId", unidad("g"))))).estado()).isEqualTo(400);
        assertThat(put("ADMIN", "/api/recetas/" + taco, m("detalles", List.of(
                m("itemId", carne, "cantidad", d("0"), "unidadId", unidad("g"))))).estado()).isEqualTo(400);
        // Un cajero no puede modificar recetas
        assertThat(put("CAJERO", "/api/recetas/" + taco, m("detalles", List.of())).estado()).isEqualTo(403);
        // La receta valida sigue igual tras los intentos rechazados
        assertThat(fila(taco).get("costoReceta").decimalValue()).isEqualByComparingTo("0.75");
    }

    private JsonNode fila(long productoId) throws Exception {
        for (JsonNode n : get("ADMIN", "/api/recetas").ok().cuerpo()) {
            if (n.get("productoId").asLong() == productoId) return n;
        }
        throw new AssertionError("No aparece el producto " + productoId);
    }

    @Test
    void prueba06_entradasSalidasYAjustes() throws Exception {
        long id = crearItem("Cebolla P02", "kg", "10", "2", "1.00");
        long usuario = usuario("INVENTARIO").getId();

        Resp entrada = post("INVENTARIO", "/api/inventario/movimientos", m("itemId", id, "tipo", "ENTRADA", "cantidad", d("5"),
                "costoUnitario", d("1.20"), "motivo", "Compra en mercado"));
        assertThat(entrada.estado()).isEqualTo(201);
        assertThat(existencia(id)).isEqualByComparingTo("15");
        assertThat(jdbc.queryForObject("SELECT costo_unitario FROM inventario_items WHERE id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("1.20");

        post("INVENTARIO", "/api/inventario/movimientos", m("itemId", id, "tipo", "SALIDA", "cantidad", d("3"), "motivo", "Merma")).ok();
        assertThat(existencia(id)).isEqualByComparingTo("12");

        // Una salida mayor que la existencia se rechaza y no cambia nada
        Resp excesiva = post("INVENTARIO", "/api/inventario/movimientos", m("itemId", id, "tipo", "SALIDA", "cantidad", d("100"), "motivo", "x"));
        assertThat(excesiva.estado()).isEqualTo(409);
        assertThat(excesiva.mensaje()).contains("Existencia insuficiente");
        assertThat(existencia(id)).isEqualByComparingTo("12");

        // Ajuste por conteo fisico: la cantidad es la existencia contada
        post("INVENTARIO", "/api/inventario/movimientos", m("itemId", id, "tipo", "AJUSTE", "cantidad", d("11.5"), "motivo", "Conteo semanal")).ok();
        assertThat(existencia(id)).isEqualByComparingTo("11.5");
        assertThat(post("INVENTARIO", "/api/inventario/movimientos", m("itemId", id, "tipo", "AJUSTE", "cantidad", d("11.5"), "motivo", "x"))
                .estado()).isEqualTo(400);

        // Sin motivo no se acepta; tipos automaticos no se pueden registrar a mano
        assertThat(post("INVENTARIO", "/api/inventario/movimientos", m("itemId", id, "tipo", "ENTRADA", "cantidad", d("1"), "motivo", ""))
                .estado()).isEqualTo(400);
        assertThat(post("ADMIN", "/api/inventario/movimientos", m("itemId", id, "tipo", "VENTA", "cantidad", d("1"), "motivo", "x"))
                .estado()).isEqualTo(400);

        // Cada cambio quedo registrado con usuario, existencia anterior y nueva
        List<Map<String, Object>> movs = jdbc.queryForList("""
                SELECT tipo, cantidad, existencia_anterior, existencia_nueva, usuario_id FROM movimientos_inventario
                WHERE item_id = ? ORDER BY id""", id);
        assertThat(movs).hasSize(4);
        assertThat(movs.subList(1, 4)).allSatisfy(mv -> assertThat(((Number) mv.get("usuario_id")).longValue()).isEqualTo(usuario));
        assertThat((BigDecimal) movs.get(3).get("cantidad")).isEqualByComparingTo("-0.5");
        assertThat((BigDecimal) movs.get(3).get("existencia_anterior")).isEqualByComparingTo("12");
        // La existencia actual coincide con la suma de los movimientos
        BigDecimal suma = movs.stream().map(mv -> (BigDecimal) mv.get("cantidad")).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo(existencia(id));
    }
}
