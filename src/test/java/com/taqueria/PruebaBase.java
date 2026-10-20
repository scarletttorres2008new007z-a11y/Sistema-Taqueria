package com.taqueria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taqueria.model.Rol;
import com.taqueria.model.Usuario;
import com.taqueria.repository.RolRepository;
import com.taqueria.repository.UsuarioRepository;
import com.taqueria.security.UsuarioSesion;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Base de las pruebas de integracion: levanta la aplicacion completa contra PostgreSQL
 * (base taqueria_test, que se limpia y migra con Flyway al iniciar) y ofrece ayudas
 * para llamar a la API como cada cargo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PruebaBase.Configuracion.class)
public abstract class PruebaBase {

    @TestConfiguration
    static class Configuracion {
        /** Borra la base de pruebas y aplica las migraciones desde cero (una vez por ejecucion). */
        @Bean
        FlywayMigrationStrategy limpiarYMigrar() {
            return (Flyway f) -> {
                String url;
                try (var conexion = f.getConfiguration().getDataSource().getConnection()) {
                    url = conexion.getMetaData().getURL();
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException(e);
                }
                if (!url.contains("test")) {
                    throw new IllegalStateException("Las pruebas borran la base de datos: use una base de pruebas (DB_TEST_URL), no " + url);
                }
                f.clean();
                f.migrate();
            };
        }
    }

    public static final String CLAVE_PRUEBA = "prueba1234";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected RolRepository roles;
    @Autowired protected PasswordEncoder encoder;

    /** Respuesta de la API: estado HTTP y cuerpo JSON. */
    public record Resp(int estado, JsonNode cuerpo, String texto) {
        public Resp ok() {
            assertThat(estado).as("HTTP %s: %s", estado, texto).isBetween(200, 299);
            return this;
        }

        public JsonNode get(String campo) {
            return cuerpo.get(campo);
        }

        public long id() {
            return cuerpo.get("id").asLong();
        }

        public BigDecimal dinero(String campo) {
            return cuerpo.get(campo).decimalValue();
        }

        public String mensaje() {
            return cuerpo != null && cuerpo.has("mensaje") ? cuerpo.get("mensaje").asText() : texto;
        }
    }

    /** Usuario de prueba con el cargo indicado (ADMIN, CAJERO, MESERO, COCINA, INVENTARIO). */
    protected UsuarioSesion usuario(String rol) {
        String nombre = "prueba." + rol.toLowerCase(Locale.ROOT);
        Usuario u = usuarios.buscarPorUsername(nombre).orElseGet(() -> {
            Usuario n = new Usuario();
            n.setUsername(nombre);
            n.setNombre("Prueba " + rol);
            n.setPasswordHash(encoder.encode(CLAVE_PRUEBA));
            Rol r = roles.findByCodigo(rol).orElseThrow();
            n.setRol(r);
            return usuarios.save(n);
        });
        return new UsuarioSesion(usuarios.findById(u.getId()).orElseThrow());
    }

    protected Resp llamar(String rol, HttpMethod metodo, String url, Object cuerpo, String clave) throws Exception {
        MockHttpServletRequestBuilder r = request(metodo, url).accept(MediaType.APPLICATION_JSON).with(csrf());
        if (rol != null) r.with(user(usuario(rol)));
        if (cuerpo != null) r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo));
        if (clave != null) r.header("Idempotency-Key", clave);
        MvcResult res = mvc.perform(r).andReturn();
        String texto = res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode nodo = texto.isBlank() || !texto.trim().startsWith("{") && !texto.trim().startsWith("[") ? null : json.readTree(texto);
        return new Resp(res.getResponse().getStatus(), nodo, texto);
    }

    protected Resp get(String rol, String url) throws Exception {
        return llamar(rol, HttpMethod.GET, url, null, null);
    }

    protected Resp post(String rol, String url, Object cuerpo) throws Exception {
        return llamar(rol, HttpMethod.POST, url, cuerpo, null);
    }

    protected Resp post(String rol, String url, Object cuerpo, String clave) throws Exception {
        return llamar(rol, HttpMethod.POST, url, cuerpo, clave);
    }

    protected Resp put(String rol, String url, Object cuerpo) throws Exception {
        return llamar(rol, HttpMethod.PUT, url, cuerpo, null);
    }

    protected static Map<String, Object> m(Object... kv) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) mapa.put((String) kv[i], kv[i + 1]);
        return mapa;
    }

    protected static String clave() {
        return UUID.randomUUID().toString();
    }

    protected static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    // ---------- Datos de apoyo creados por API ----------

    protected long unidad(String codigo) {
        return jdbc.queryForObject("SELECT id FROM unidades_medida WHERE codigo = ?", Long.class, codigo);
    }

    protected long categoria(String nombre) {
        return jdbc.queryForObject("SELECT id FROM categorias WHERE nombre = ?", Long.class, nombre);
    }

    protected long crearItem(String nombre, String unidad, String existencia, String minimo, String costo) throws Exception {
        return post("ADMIN", "/api/inventario", m("nombre", nombre, "tipo", "INGREDIENTE", "categoria", "Pruebas",
                "unidadId", unidad(unidad), "existenciaInicial", d(existencia), "existenciaMinima", d(minimo),
                "costoUnitario", d(costo), "activo", true)).ok().id();
    }

    protected long crearProducto(String nombre, String categoria, String precio) throws Exception {
        return post("ADMIN", "/api/productos", m("nombre", nombre, "categoriaId", categoria(categoria), "tipo", "PREPARADO",
                "precio", d(precio), "costoEstimado", d("0"), "activo", true, "disponible", true)).ok().id();
    }

    protected void receta(long productoId, Object... itemCantidadUnidad) throws Exception {
        List<Map<String, Object>> det = new ArrayList<>();
        for (int i = 0; i < itemCantidadUnidad.length; i += 3) {
            det.add(m("itemId", itemCantidadUnidad[i], "cantidad", d((String) itemCantidadUnidad[i + 1]),
                    "unidadId", unidad((String) itemCantidadUnidad[i + 2])));
        }
        put("ADMIN", "/api/recetas/" + productoId, m("notas", null, "detalles", det)).ok();
    }

    protected BigDecimal existencia(long itemId) {
        return jdbc.queryForObject("SELECT existencia FROM inventario_items WHERE id = ?", BigDecimal.class, itemId);
    }

    /** Abre la caja si no hay una abierta (la caja es unica para todo el negocio). */
    protected void asegurarCajaAbierta() throws Exception {
        Resp r = get("CAJERO", "/api/caja/actual").ok();
        if (!r.get("abierta").asBoolean()) post("CAJERO", "/api/caja/abrir", m("montoInicial", d("50.00"))).ok();
    }

    protected Map<String, Object> item(long productoId, int cantidad) {
        return m("productoId", productoId, "cantidad", cantidad, "observaciones", null, "complementoIds", List.of());
    }

    protected Resp crearPedidoLlevar(List<Map<String, Object>> items) throws Exception {
        return post("CAJERO", "/api/pedidos", m("tipo", "LLEVAR", "items", items), clave()).ok();
    }

    protected Map<String, Object> pagoEfectivo(String monto, String recibido) {
        return m("metodo", "EFECTIVO", "monto", d(monto), "montoRecibido", recibido == null ? null : d(recibido));
    }

    protected long contar(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
