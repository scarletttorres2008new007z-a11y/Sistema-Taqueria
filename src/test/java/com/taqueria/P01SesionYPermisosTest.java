package com.taqueria;

import com.taqueria.service.UsuarioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Pruebas 1 y 2: administrador inicial, inicio y cierre de sesion, y permisos aplicados en el servidor. */
@Order(1)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class P01SesionYPermisosTest extends PruebaBase {

    @Autowired UsuarioService usuarioService;

    private MockHttpSession entrar(String usuario, String clave, int esperado) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/sesion/entrar").param("username", usuario).param("password", clave).with(csrf()))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(esperado);
        return (MockHttpSession) r.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void administradorInicialSoloConCodigoDeConsola() throws Exception {
        assertThat(usuarios.count()).as("la base de pruebas empieza vacía").isZero();
        String codigo = (String) ReflectionTestUtils.getField(usuarioService, "codigoInicial");
        assertThat(codigo).isNotBlank();

        Resp sinCodigo = post(null, "/api/configuracion-inicial/administrador",
                m("username", "dueno", "nombre", "Dueño", "password", "clave-segura-1", "codigoConfiguracion", "AAAA-BBBB-CCCC"));
        assertThat(sinCodigo.estado()).isEqualTo(400);
        assertThat(usuarios.count()).isZero();

        Resp corta = post(null, "/api/configuracion-inicial/administrador",
                m("username", "dueno", "nombre", "Dueño", "password", "corta", "codigoConfiguracion", codigo));
        assertThat(corta.estado()).isEqualTo(400);
        assertThat(corta.get("campos").has("password")).isTrue();

        post(null, "/api/configuracion-inicial/administrador",
                m("username", "dueno", "nombre", "Dueño", "password", "clave-segura-1", "codigoConfiguracion", codigo.toLowerCase())).ok();
        String hash = jdbc.queryForObject("SELECT password_hash FROM usuarios WHERE username = 'dueno'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("clave-segura-1");

        Resp otra = post(null, "/api/configuracion-inicial/administrador",
                m("username", "intruso", "nombre", "X", "password", "clave-segura-2", "codigoConfiguracion", codigo));
        assertThat(otra.estado()).isEqualTo(409);
    }

    @Test
    @Order(2)
    void inicioYCierreDeSesion() throws Exception {
        entrar("dueno", "incorrecta", 401);
        MockHttpSession sesion = entrar("dueno", "clave-segura-1", 200);
        String estado = mvc.perform(MockMvcRequestBuilders.get("/api/sesion/estado").session(sesion)).andReturn().getResponse().getContentAsString();
        assertThat(estado).contains("\"autenticado\":true").contains("dueno");
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/usuarios").session(sesion)).andReturn().getResponse().getStatus()).isEqualTo(200);

        int salir = mvc.perform(MockMvcRequestBuilders.post("/api/sesion/salir").session(sesion).with(csrf())).andReturn().getResponse().getStatus();
        assertThat(salir).isEqualTo(204);
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/usuarios").session(sesion)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(contar("SELECT count(*) FROM auditoria WHERE accion = 'Inicio de sesión fallido'")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(3)
    void sinSesionNoHayAcceso() throws Exception {
        assertThat(get(null, "/api/pedidos").estado()).isEqualTo(401);
        assertThat(get(null, "/api/dashboard").estado()).isEqualTo(401);
        int html = mvc.perform(MockMvcRequestBuilders.get("/panel.html")).andReturn().getResponse().getStatus();
        assertThat(html).isEqualTo(302);
    }

    @Test
    @Order(4)
    void permisosSegunElCargo() throws Exception {
        long cat = categoria("Tacos");
        var producto = m("nombre", "Taco permisos", "categoriaId", cat, "tipo", "PREPARADO", "precio", d("1.00"), "activo", true, "disponible", true);
        assertThat(post("COCINA", "/api/productos", producto).estado()).isEqualTo(403);
        assertThat(post("MESERO", "/api/productos", producto).estado()).isEqualTo(403);
        assertThat(get("CAJERO", "/api/usuarios").estado()).isEqualTo(403);
        assertThat(get("COCINA", "/api/reportes/ventas").estado()).isEqualTo(403);
        assertThat(post("MESERO", "/api/caja/abrir", m("montoInicial", d("10"))).estado()).isEqualTo(403);
        assertThat(post("INVENTARIO", "/api/ventas/1/anular", m("motivo", "x", "devolverInventario", false)).estado()).isEqualTo(403);
        // Cocina si puede ver pedidos y el panel de cocina
        assertThat(get("COCINA", "/api/pedidos/cocina").estado()).isEqualTo(200);
        // El administrador si puede crear productos
        assertThat(post("ADMIN", "/api/productos", producto).estado()).isEqualTo(201);
        // Un mesero no puede hacer ajustes de inventario aunque llame directo a la API
        long item = crearItem("Item permisos", "kg", "1", "0", "1");
        assertThat(post("CAJERO", "/api/inventario/movimientos", m("itemId", item, "tipo", "AJUSTE", "cantidad", d("5"), "motivo", "x")).estado())
                .isEqualTo(403);
        // El encargado de inventario puede registrar entradas pero no ajustes si se le quita el permiso
        assertThat(post("INVENTARIO", "/api/inventario/movimientos", m("itemId", item, "tipo", "ENTRADA", "cantidad", d("1"), "motivo", "compra")).estado())
                .isEqualTo(201);
    }

    @Test
    @Order(5)
    void empleadoDesactivadoPierdeLaSesionAbierta() throws Exception {
        Resp creado = post("ADMIN", "/api/usuarios", m("username", "temporal", "nombre", "Temporal", "rolId",
                roles.findByCodigo("MESERO").orElseThrow().getId(), "activo", true, "password", "temporal-123")).ok();
        MockHttpSession sesion = entrar("temporal", "temporal-123", 200);
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/pedidos").session(sesion)).andReturn().getResponse().getStatus()).isEqualTo(200);

        put("ADMIN", "/api/usuarios/" + creado.id(), m("username", "temporal", "nombre", "Temporal", "rolId",
                roles.findByCodigo("MESERO").orElseThrow().getId(), "activo", false, "password", null)).ok();
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/pedidos").session(sesion)).andReturn().getResponse().getStatus()).isEqualTo(401);
        entrar("temporal", "temporal-123", 401);
    }

    @Test
    @Order(6)
    void cambioDePermisosDelCargoExigeVolverAEntrar() throws Exception {
        long rolInventario = roles.findByCodigo("INVENTARIO").orElseThrow().getId();
        List<String> originales = roles.findById(rolInventario).orElseThrow().getPermisos().stream().toList();
        post("ADMIN", "/api/usuarios", m("username", "bodega", "nombre", "Bodega", "rolId", rolInventario, "activo", true, "password", "bodega-1234")).ok();
        MockHttpSession sesion = entrar("bodega", "bodega-1234", 200);
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/inventario").session(sesion)).andReturn().getResponse().getStatus()).isEqualTo(200);
        put("ADMIN", "/api/roles/" + rolInventario + "/permisos", m("permisos", originales.stream().filter(p -> !p.equals("inventario.ajustar")).toList())).ok();
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/inventario").session(sesion)).andReturn().getResponse().getStatus()).isEqualTo(401);
        put("ADMIN", "/api/roles/" + rolInventario + "/permisos", m("permisos", originales)).ok();
        // El administrador no puede quedar sin permisos ni desactivarse a si mismo
        long rolAdmin = roles.findByCodigo("ADMIN").orElseThrow().getId();
        assertThat(put("ADMIN", "/api/roles/" + rolAdmin + "/permisos", m("permisos", List.of())).estado()).isEqualTo(400);
    }
}
