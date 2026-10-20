package com.taqueria.config;

import com.taqueria.dto.Dtos.*;
import com.taqueria.model.*;
import com.taqueria.repository.*;
import com.taqueria.security.UsuarioSesion;
import com.taqueria.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Datos de DEMOSTRACION opcionales. Solo se cargan si CARGAR_DEMO=true, la base no tiene
 * productos y se define DEMO_PASSWORD (contrasena de los usuarios de demostracion).
 * Todo lo que se crea queda marcado con demo = true y los nombres indican "(demo)".
 * Los precios son de ejemplo y no representan precios reales de ningun negocio.
 */
@Component
public class DatosDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    private final boolean cargar;
    private final String password;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final ConfiguracionRepository configuracion;
    private final UnidadMedidaRepository unidades;
    private final CategoriaRepository categorias;
    private final MesaRepository mesas;
    private final ProveedorRepository proveedores;
    private final ClienteRepository clientes;
    private final InventarioService inventario;
    private final CatalogoService catalogo;
    private final RecetaService recetas;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    public DatosDemo(@Value("${taqueria.demo.cargar:false}") boolean cargar,
                     @Value("${taqueria.demo.password:}") String password,
                     ProductoRepository productos, UsuarioRepository usuarios, RolRepository roles,
                     ConfiguracionRepository configuracion, UnidadMedidaRepository unidades,
                     CategoriaRepository categorias, MesaRepository mesas, ProveedorRepository proveedores,
                     ClienteRepository clientes, InventarioService inventario, CatalogoService catalogo,
                     RecetaService recetas, PasswordEncoder encoder, TransactionTemplate tx) {
        this.cargar = cargar;
        this.password = password;
        this.productos = productos;
        this.usuarios = usuarios;
        this.roles = roles;
        this.configuracion = configuracion;
        this.unidades = unidades;
        this.categorias = categorias;
        this.mesas = mesas;
        this.proveedores = proveedores;
        this.clientes = clientes;
        this.inventario = inventario;
        this.catalogo = catalogo;
        this.recetas = recetas;
        this.encoder = encoder;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!cargar) return;
        if (productos.count() > 0) {
            log.info("Datos de demostración: la base ya tiene productos, no se cargan.");
            return;
        }
        if (password == null || password.length() < 8) {
            log.warn("Datos de demostración: defina DEMO_PASSWORD (mínimo 8 caracteres) para crear los usuarios de prueba.");
            return;
        }
        tx.executeWithoutResult(s -> cargar());
        log.info("Datos de demostración cargados. Usuarios: admin.demo, cajero.demo, mesero.demo, cocina.demo, inventario.demo");
    }

    private void cargar() {
        Usuario admin = usuario("admin.demo", "Administrador (demo)", "ADMIN");
        usuario("cajero.demo", "Cajero (demo)", "CAJERO");
        usuario("mesero.demo", "Mesero (demo)", "MESERO");
        usuario("cocina.demo", "Cocina (demo)", "COCINA");
        usuario("inventario.demo", "Encargado de inventario (demo)", "INVENTARIO");
        UsuarioSesion sesion = new UsuarioSesion(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sesion, null, sesion.getAuthorities()));
        try {
            cargarCatalogo();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Usuario usuario(String username, String nombre, String rol) {
        return usuarios.buscarPorUsername(username).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setUsername(username);
            u.setNombre(nombre);
            u.setPasswordHash(encoder.encode(password));
            u.setRol(roles.findByCodigo(rol).orElseThrow());
            u.setDemo(true);
            return usuarios.save(u);
        });
    }

    private void cargarCatalogo() {
        Configuracion cfg = configuracion.findById(Configuracion.ID).orElseThrow();
        cfg.setNombreComercial("Taquería Demo (datos de demostración)");
        cfg.setDireccion("Dirección de ejemplo - configure la real");
        cfg.setTelefono("0000-0000");
        cfg.setMensajeComprobante("Gracias por su compra - DATOS DE DEMOSTRACIÓN");
        cfg.setDatosDemo(true);
        configuracion.save(cfg);

        for (int i = 1; i <= 6; i++) catalogo.guardarMesa(null, new MesaRequest("Mesa " + i, 4, true));

        Proveedor carnes = proveedor("Carnicería Ejemplo (demo)", "Carnes de res, cerdo y pollo");
        Proveedor tortillas = proveedor("Tortillería Ejemplo (demo)", "Tortillas de maíz y harina");
        Proveedor verduras = proveedor("Verdulería Ejemplo (demo)", "Verduras, frutas y hierbas");
        Proveedor bebidas = proveedor("Distribuidora de Bebidas Ejemplo (demo)", "Refrescos y agua");

        Map<String, UnidadMedida> u = new HashMap<>();
        unidades.findAll().forEach(x -> u.put(x.getCodigo(), x));
        LocalDate hoy = Fechas.hoy();

        InventarioItem res = item("Carne de res (asada)", TipoItem.INGREDIENTE, "Carnes", u.get("kg"), 12, 3, "9.50", carnes, hoy.plusDays(4));
        InventarioItem cerdo = item("Carne de cerdo (pastor)", TipoItem.INGREDIENTE, "Carnes", u.get("kg"), 10, 3, "6.80", carnes, hoy.plusDays(4));
        InventarioItem pollo = item("Pollo", TipoItem.INGREDIENTE, "Carnes", u.get("kg"), 8, 3, "5.20", carnes, hoy.plusDays(2));
        InventarioItem tortMaiz = item("Tortilla de maíz", TipoItem.INGREDIENTE, "Tortillas", u.get("u"), 600, 150, "0.04", tortillas, hoy.plusDays(3));
        InventarioItem tortHarina = item("Tortilla de harina grande", TipoItem.INGREDIENTE, "Tortillas", u.get("u"), 80, 20, "0.15", tortillas, hoy.plusDays(10));
        InventarioItem queso = item("Queso Oaxaca", TipoItem.INGREDIENTE, "Lácteos", u.get("kg"), 4, 1.5, "7.50", null, hoy.plusDays(6));
        InventarioItem cebolla = item("Cebolla", TipoItem.INGREDIENTE, "Verduras", u.get("kg"), 6, 2, "1.10", verduras, null);
        InventarioItem cilantro = item("Cilantro", TipoItem.INGREDIENTE, "Verduras", u.get("kg"), 0.8, 1, "3.50", verduras, hoy.plusDays(2));
        InventarioItem tomate = item("Tomate", TipoItem.INGREDIENTE, "Verduras", u.get("kg"), 5, 2, "1.40", verduras, hoy.plusDays(5));
        InventarioItem pina = item("Piña", TipoItem.INGREDIENTE, "Verduras", u.get("kg"), 3, 1, "0.90", verduras, hoy.plusDays(5));
        InventarioItem salsaVerde = item("Salsa verde", TipoItem.INGREDIENTE, "Salsas", u.get("l"), 4, 1, "2.00", null, hoy.plusDays(7));
        InventarioItem salsaRoja = item("Salsa roja", TipoItem.INGREDIENTE, "Salsas", u.get("l"), 4, 1, "2.00", null, hoy.plusDays(7));
        InventarioItem frijoles = item("Frijoles cocidos", TipoItem.INGREDIENTE, "Abarrotes", u.get("kg"), 5, 2, "1.60", null, hoy.plusDays(3));
        InventarioItem arroz = item("Arroz cocido", TipoItem.INGREDIENTE, "Abarrotes", u.get("kg"), 4, 1, "1.20", null, hoy.plusDays(2));
        InventarioItem horchata = item("Concentrado de horchata", TipoItem.INGREDIENTE, "Bebidas", u.get("l"), 6, 2, "1.20", null, hoy.plusDays(20));
        InventarioItem agua = item("Agua embotellada 600 ml", TipoItem.PRODUCTO_TERMINADO, "Bebidas", u.get("u"), 48, 12, "0.25", bebidas, null);
        InventarioItem refresco = item("Refresco en lata", TipoItem.PRODUCTO_TERMINADO, "Bebidas", u.get("u"), 6, 12, "0.45", bebidas, null);
        InventarioItem vaso = item("Vaso desechable 16 oz", TipoItem.MATERIAL, "Desechables", u.get("u"), 200, 50, "0.05", null, null);

        Map<String, Categoria> cat = new HashMap<>();
        categorias.findAll().forEach(c -> cat.put(c.getNombre(), c));

        Producto tAsada = producto("Taco de asada", cat.get("Tacos"), TipoProducto.PREPARADO, "1.50", null);
        receta(tAsada, d(tortMaiz, "2", u.get("u")), d(res, "70", u.get("g")), d(cebolla, "10", u.get("g")),
                d(cilantro, "5", u.get("g")), d(salsaVerde, "15", u.get("ml")));
        Producto tPastor = producto("Taco al pastor", cat.get("Tacos"), TipoProducto.PREPARADO, "1.25", null);
        receta(tPastor, d(tortMaiz, "2", u.get("u")), d(cerdo, "60", u.get("g")), d(pina, "15", u.get("g")),
                d(cebolla, "10", u.get("g")), d(cilantro, "5", u.get("g")), d(salsaRoja, "15", u.get("ml")));
        Producto tPollo = producto("Taco de pollo", cat.get("Tacos"), TipoProducto.PREPARADO, "1.25", null);
        receta(tPollo, d(tortMaiz, "2", u.get("u")), d(pollo, "70", u.get("g")), d(cebolla, "10", u.get("g")),
                d(cilantro, "5", u.get("g")), d(salsaVerde, "15", u.get("ml")));
        Producto quesadilla = producto("Quesadilla", cat.get("Quesadillas"), TipoProducto.PREPARADO, "3.00", null);
        receta(quesadilla, d(tortHarina, "1", u.get("u")), d(queso, "80", u.get("g")), d(salsaRoja, "20", u.get("ml")));
        Producto burrito = producto("Burrito de asada", cat.get("Burritos"), TipoProducto.PREPARADO, "5.50", null);
        receta(burrito, d(tortHarina, "1", u.get("u")), d(res, "150", u.get("g")), d(frijoles, "60", u.get("g")),
                d(arroz, "60", u.get("g")), d(queso, "30", u.get("g")), d(tomate, "30", u.get("g")));
        Producto horch = producto("Horchata", cat.get("Bebidas"), TipoProducto.PREPARADO, "1.50", null);
        receta(horch, d(horchata, "250", u.get("ml")), d(vaso, "1", u.get("u")));
        producto("Agua embotellada", cat.get("Bebidas"), TipoProducto.REVENTA, "1.00", agua);
        producto("Refresco en lata", cat.get("Bebidas"), TipoProducto.REVENTA, "1.25", refresco);
        // Producto sin receta a proposito, para mostrar el aviso de "receta no configurada"
        producto("Torta de asada", cat.get("Tortas"), TipoProducto.PREPARADO, "4.50", null);

        catalogo.guardarComplemento(null, new ComplementoRequest("Queso extra", new BigDecimal("0.50"), null, true));
        catalogo.guardarComplemento(null, new ComplementoRequest("Doble carne", new BigDecimal("0.75"), cat.get("Tacos").getId(), true));
        catalogo.guardarComplemento(null, new ComplementoRequest("Guacamole", new BigDecimal("0.60"), null, true));

        Cliente c = new Cliente();
        c.setNombre("Cliente de ejemplo (demo)");
        c.setTelefono("0000-0001");
        c.setDireccion("Colonia de ejemplo, casa 1");
        c.setDemo(true);
        clientes.save(c);
    }

    private Proveedor proveedor(String nombre, String notas) {
        Proveedor p = new Proveedor();
        p.setNombreComercial(nombre);
        p.setContacto("Contacto de ejemplo");
        p.setTelefono("0000-0000");
        p.setNotas(notas);
        p.setDemo(true);
        return proveedores.save(p);
    }

    private InventarioItem item(String nombre, TipoItem tipo, String categoria, UnidadMedida unidad, double existencia,
                                double minimo, String costo, Proveedor proveedor, LocalDate vence) {
        InventarioItem it = inventario.guardarItem(null, new ItemInventarioRequest(null, nombre, tipo, categoria, unidad.getId(),
                BigDecimal.valueOf(existencia), BigDecimal.valueOf(minimo), new BigDecimal(costo),
                proveedor == null ? null : proveedor.getId(), vence, null, true));
        it.setDemo(true);
        return it;
    }

    private Producto producto(String nombre, Categoria cat, TipoProducto tipo, String precio, InventarioItem item) {
        Producto p = catalogo.guardarProducto(null, new ProductoRequest(null, nombre, "Producto de demostración, precio de ejemplo",
                cat.getId(), tipo, new BigDecimal(precio), BigDecimal.ZERO, item == null ? null : item.getId(), true, true));
        p.setDemo(true);
        return productos.save(p);
    }

    private RecetaDetalleRequest d(InventarioItem item, String cantidad, UnidadMedida unidad) {
        return new RecetaDetalleRequest(item.getId(), new BigDecimal(cantidad), unidad.getId());
    }

    private void receta(Producto p, RecetaDetalleRequest... detalles) {
        recetas.guardar(p.getId(), new RecetaRequest("Receta de demostración", List.of(detalles)));
    }
}
