package com.taqueria.web;

import com.taqueria.dto.Dtos.*;
import com.taqueria.model.*;
import com.taqueria.service.CatalogoService;
import com.taqueria.service.RecetaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Menu: categorias, productos, complementos, recetas y mesas. */
@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CatalogoService catalogo;
    private final RecetaService recetas;

    public CatalogoController(CatalogoService catalogo, RecetaService recetas) {
        this.catalogo = catalogo;
        this.recetas = recetas;
    }

    @GetMapping("/categorias")
    @PreAuthorize("hasAuthority('productos.ver')")
    public List<Categoria> categorias() {
        return catalogo.categorias();
    }

    @PostMapping("/categorias")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Categoria crearCategoria(@Valid @RequestBody CategoriaRequest req) {
        return catalogo.guardarCategoria(null, req);
    }

    @PutMapping("/categorias/{id}")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    public Categoria editarCategoria(@PathVariable Long id, @Valid @RequestBody CategoriaRequest req) {
        return catalogo.guardarCategoria(id, req);
    }

    @GetMapping("/productos")
    @PreAuthorize("hasAuthority('productos.ver')")
    public List<Producto> productos() {
        return catalogo.productos();
    }

    @GetMapping("/productos/{id}")
    @PreAuthorize("hasAuthority('productos.ver')")
    public Producto producto(@PathVariable Long id) {
        return catalogo.producto(id);
    }

    @PostMapping("/productos")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Producto crearProducto(@Valid @RequestBody ProductoRequest req) {
        return catalogo.guardarProducto(null, req);
    }

    @PutMapping("/productos/{id}")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    public Producto editarProducto(@PathVariable Long id, @Valid @RequestBody ProductoRequest req) {
        return catalogo.guardarProducto(id, req);
    }

    @PostMapping("/productos/{id}/desactivar")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    public Producto desactivarProducto(@PathVariable Long id) {
        return catalogo.desactivarProducto(id);
    }

    @PostMapping("/productos/{id}/imagen")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    public Producto imagen(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return catalogo.subirImagen(id, archivo);
    }

    @GetMapping("/complementos")
    @PreAuthorize("hasAuthority('productos.ver')")
    public List<Complemento> complementos() {
        return catalogo.complementos();
    }

    @PostMapping("/complementos")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Complemento crearComplemento(@Valid @RequestBody ComplementoRequest req) {
        return catalogo.guardarComplemento(null, req);
    }

    @PutMapping("/complementos/{id}")
    @PreAuthorize("hasAuthority('productos.gestionar')")
    public Complemento editarComplemento(@PathVariable Long id, @Valid @RequestBody ComplementoRequest req) {
        return catalogo.guardarComplemento(id, req);
    }

    @GetMapping("/mesas")
    @PreAuthorize("hasAnyAuthority('pedidos.ver', 'productos.ver')")
    public List<Mesa> mesas() {
        return catalogo.mesas();
    }

    @PostMapping("/mesas")
    @PreAuthorize("hasAuthority('configuracion.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Mesa crearMesa(@Valid @RequestBody MesaRequest req) {
        return catalogo.guardarMesa(null, req);
    }

    @PutMapping("/mesas/{id}")
    @PreAuthorize("hasAuthority('configuracion.gestionar')")
    public Mesa editarMesa(@PathVariable Long id, @Valid @RequestBody MesaRequest req) {
        return catalogo.guardarMesa(id, req);
    }

    // ---------- Recetas ----------

    @GetMapping("/recetas")
    @PreAuthorize("hasAuthority('productos.ver')")
    public List<Map<String, Object>> recetas() {
        return recetas.resumen();
    }

    @GetMapping("/recetas/{productoId}")
    @PreAuthorize("hasAuthority('productos.ver')")
    public ResponseEntity<Map<String, Object>> receta(@PathVariable Long productoId) {
        Producto p = catalogo.producto(productoId);
        return recetas.obtener(productoId)
                .map(r -> ResponseEntity.ok(Map.<String, Object>of("producto", p, "receta", r, "costo", recetas.costo(r),
                        "configurada", true)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("producto", p, "configurada", false,
                        "mensaje", "Este producto no tiene receta: el descuento automático de inventario no está configurado.")));
    }

    @PutMapping("/recetas/{productoId}")
    @PreAuthorize("hasAuthority('recetas.gestionar')")
    public Receta guardarReceta(@PathVariable Long productoId, @Valid @RequestBody RecetaRequest req) {
        return recetas.guardar(productoId, req);
    }

    @DeleteMapping("/recetas/{productoId}")
    @PreAuthorize("hasAuthority('recetas.gestionar')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarReceta(@PathVariable Long productoId) {
        recetas.eliminar(productoId);
    }
}
