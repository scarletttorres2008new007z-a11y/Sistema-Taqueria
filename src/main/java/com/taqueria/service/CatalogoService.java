package com.taqueria.service;

import com.taqueria.dto.Dtos.*;
import com.taqueria.model.*;
import com.taqueria.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Categorias, productos, complementos y mesas. */
@Service
public class CatalogoService {

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final ComplementoRepository complementos;
    private final MesaRepository mesas;
    private final InventarioItemRepository items;
    private final ArchivoService archivos;
    private final AuditoriaService auditoria;

    public CatalogoService(CategoriaRepository categorias, ProductoRepository productos, ComplementoRepository complementos,
                           MesaRepository mesas, InventarioItemRepository items, ArchivoService archivos,
                           AuditoriaService auditoria) {
        this.categorias = categorias;
        this.productos = productos;
        this.complementos = complementos;
        this.mesas = mesas;
        this.items = items;
        this.archivos = archivos;
        this.auditoria = auditoria;
    }

    // ---------- Categorias ----------

    public List<Categoria> categorias() {
        return categorias.findAllByOrderByOrdenAscNombreAsc();
    }

    @Transactional
    public Categoria guardarCategoria(Long id, CategoriaRequest req) {
        Categoria c = id == null ? new Categoria() : categorias.findById(id).orElseThrow(() -> ApiException.noEncontrado("Categoría"));
        c.setNombre(req.nombre().trim());
        c.setOrden(req.orden());
        c.setActiva(req.activa());
        try {
            categorias.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("Ya existe una categoría con ese nombre");
        }
        auditoria.registrar(id == null ? "Categoría creada" : "Categoría modificada", "Categoria", c.getId(), c.getNombre());
        return c;
    }

    // ---------- Productos ----------

    public List<Producto> productos() {
        return productos.findAllByOrderByCategoriaOrdenAscNombreAsc();
    }

    public Producto producto(Long id) {
        return productos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Producto"));
    }

    @Transactional
    public Producto guardarProducto(Long id, ProductoRequest req) {
        Producto p = id == null ? new Producto() : producto(id);
        String codigo = Texto.limpio(req.codigo());
        if (codigo == null) {
            codigo = p.getCodigo() != null ? p.getCodigo() : String.format("PR-%04d", productos.maxId() + 1);
        }
        if (!codigo.equalsIgnoreCase(p.getCodigo() == null ? "" : p.getCodigo()) && productos.existsByCodigoIgnoreCase(codigo)) {
            throw ApiException.conflicto("Ya existe un producto con el código " + codigo);
        }
        Categoria cat = categorias.findById(req.categoriaId()).orElseThrow(() -> ApiException.noEncontrado("Categoría"));
        InventarioItem item = null;
        if (req.tipo() == TipoProducto.REVENTA) {
            if (req.itemInventarioId() == null) {
                throw new ApiException("Un producto de reventa debe indicar qué artículo de inventario descuenta");
            }
            item = items.findById(req.itemInventarioId()).orElseThrow(() -> ApiException.noEncontrado("Artículo de inventario"));
        }
        String precioAnterior = p.getPrecio() == null ? null : p.getPrecio().toPlainString();
        p.setCodigo(codigo.toUpperCase());
        p.setNombre(req.nombre().trim());
        p.setDescripcion(Texto.limpio(req.descripcion()));
        p.setCategoria(cat);
        p.setTipo(req.tipo());
        p.setPrecio(Dinero.r(req.precio()));
        p.setCostoEstimado(Dinero.r(req.costoEstimado()));
        p.setItemInventario(item);
        p.setActivo(req.activo());
        p.setDisponible(req.disponible());
        productos.save(p);
        String detalle = p.getCodigo() + " " + p.getNombre() + " $" + p.getPrecio();
        if (precioAnterior != null && !precioAnterior.equals(p.getPrecio().toPlainString())) {
            detalle += " (precio anterior $" + precioAnterior + ")";
        }
        auditoria.registrar(id == null ? "Producto creado" : "Producto modificado", "Producto", p.getId(), detalle);
        return p;
    }

    /** Los productos no se eliminan para conservar el historial de ventas; solo se desactivan. */
    @Transactional
    public Producto desactivarProducto(Long id) {
        Producto p = producto(id);
        p.setActivo(false);
        p.setDisponible(false);
        productos.save(p);
        auditoria.registrar("Producto desactivado", "Producto", p.getId(), p.getNombre());
        return p;
    }

    @Transactional
    public Producto subirImagen(Long id, MultipartFile archivo) {
        Producto p = producto(id);
        p.setImagenUrl(archivos.guardarImagen(archivo, "productos"));
        productos.save(p);
        auditoria.registrar("Imagen de producto actualizada", "Producto", p.getId(), p.getNombre());
        return p;
    }

    // ---------- Complementos ----------

    public List<Complemento> complementos() {
        return complementos.findAllByOrderByNombreAsc();
    }

    @Transactional
    public Complemento guardarComplemento(Long id, ComplementoRequest req) {
        Complemento c = id == null ? new Complemento()
                : complementos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Complemento"));
        c.setNombre(req.nombre().trim());
        c.setPrecioExtra(Dinero.r(req.precioExtra()));
        c.setCategoria(req.categoriaId() == null ? null
                : categorias.findById(req.categoriaId()).orElseThrow(() -> ApiException.noEncontrado("Categoría")));
        c.setActivo(req.activo());
        complementos.save(c);
        auditoria.registrar(id == null ? "Complemento creado" : "Complemento modificado", "Complemento", c.getId(),
                c.getNombre() + " +$" + c.getPrecioExtra());
        return c;
    }

    // ---------- Mesas ----------

    public List<Mesa> mesas() {
        return mesas.findAllByOrderByNombreAsc();
    }

    @Transactional
    public Mesa guardarMesa(Long id, MesaRequest req) {
        Mesa m = id == null ? new Mesa() : mesas.findById(id).orElseThrow(() -> ApiException.noEncontrado("Mesa"));
        m.setNombre(req.nombre().trim());
        m.setCapacidad(req.capacidad());
        m.setActiva(req.activa());
        try {
            mesas.saveAndFlush(m);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("Ya existe una mesa con ese nombre");
        }
        auditoria.registrar(id == null ? "Mesa creada" : "Mesa modificada", "Mesa", m.getId(), m.getNombre());
        return m;
    }
}
