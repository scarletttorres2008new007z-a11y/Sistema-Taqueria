package com.taqueria.service;

import com.taqueria.dto.Dtos.CategoriaGastoRequest;
import com.taqueria.dto.Dtos.GastoRequest;
import com.taqueria.model.*;
import com.taqueria.repository.CategoriaGastoRepository;
import com.taqueria.repository.GastoRepository;
import com.taqueria.security.UsuarioActual;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** Gastos operativos (gas, agua, luz, alquiler...). Las compras de inventario van en Compras. */
@Service
public class GastoService {

    public static final String REF_GASTO = "GASTO";

    private final GastoRepository gastos;
    private final CategoriaGastoRepository categorias;
    private final CajaService caja;
    private final ArchivoService archivos;
    private final AuditoriaService auditoria;

    public GastoService(GastoRepository gastos, CategoriaGastoRepository categorias, CajaService caja,
                        ArchivoService archivos, AuditoriaService auditoria) {
        this.gastos = gastos;
        this.categorias = categorias;
        this.caja = caja;
        this.archivos = archivos;
        this.auditoria = auditoria;
    }

    public List<Gasto> buscar(LocalDate desde, LocalDate hasta, Long categoriaId) {
        LocalDate d = Fechas.o(desde, Fechas.hoy().withDayOfMonth(1));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        return gastos.buscar(d, h, categoriaId);
    }

    @Transactional
    public Gasto crear(GastoRequest req) {
        CategoriaGasto cat = categorias.findById(req.categoriaId()).orElseThrow(() -> ApiException.noEncontrado("Categoría de gasto"));
        if (!cat.isActiva()) throw new ApiException("La categoría " + cat.getNombre() + " está desactivada");
        if (req.fecha().isAfter(Fechas.hoy())) throw new ApiException("La fecha del gasto no puede ser futura");
        Gasto g = new Gasto();
        g.setFecha(req.fecha());
        g.setCategoria(cat);
        g.setConcepto(req.concepto().trim());
        g.setMonto(Dinero.r(req.monto()));
        g.setMetodoPago(req.metodoPago());
        g.setPagadoDesdeCaja(req.pagadoDesdeCaja());
        g.setNumeroComprobante(Texto.limpio(req.numeroComprobante()));
        g.setUsuarioId(UsuarioActual.id());
        if (req.pagadoDesdeCaja()) {
            if (req.metodoPago() != MetodoPago.EFECTIVO) {
                throw new ApiException("Solo los gastos pagados en efectivo pueden salir de la caja");
            }
            Caja c = caja.requerirAbierta();
            caja.validarEfectivoDisponible(c, g.getMonto());
            gastos.saveAndFlush(g);
            caja.registrar(c, TipoMovimientoCaja.GASTO, MetodoPago.EFECTIVO, g.getMonto().negate(),
                    "Gasto: " + cat.getNombre() + " - " + g.getConcepto(), REF_GASTO, g.getId());
        } else {
            gastos.saveAndFlush(g);
        }
        auditoria.registrar("Gasto registrado", "Gasto", g.getId(), cat.getNombre() + " - " + g.getConcepto()
                + " $" + g.getMonto() + (g.isPagadoDesdeCaja() ? " (desde caja)" : ""));
        return g;
    }

    @Transactional
    public Gasto anular(Long id, String motivo) {
        Gasto g = gastos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Gasto"));
        if (g.isAnulado()) throw ApiException.conflicto("El gasto ya estaba anulado");
        if (g.isPagadoDesdeCaja()) {
            Caja c = caja.requerirAbierta();
            caja.registrar(c, TipoMovimientoCaja.INGRESO, MetodoPago.EFECTIVO, g.getMonto(),
                    "Reverso de gasto anulado: " + g.getConcepto(), REF_GASTO, g.getId());
        }
        g.setAnulado(true);
        g.setMotivoAnulacion(motivo.trim());
        gastos.save(g);
        auditoria.registrar("Gasto anulado", "Gasto", g.getId(), g.getConcepto() + " $" + g.getMonto() + ": " + motivo.trim());
        return g;
    }

    @Transactional
    public Gasto adjuntarComprobante(Long id, MultipartFile archivo) {
        Gasto g = gastos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Gasto"));
        g.setComprobanteUrl(archivos.guardarImagenOPdf(archivo, "gastos"));
        gastos.save(g);
        auditoria.registrar("Comprobante de gasto adjuntado", "Gasto", g.getId(), g.getConcepto());
        return g;
    }

    public List<CategoriaGasto> categorias() {
        return categorias.findAllByOrderByNombreAsc();
    }

    @Transactional
    public CategoriaGasto guardarCategoria(Long id, CategoriaGastoRequest req) {
        CategoriaGasto c = id == null ? new CategoriaGasto()
                : categorias.findById(id).orElseThrow(() -> ApiException.noEncontrado("Categoría de gasto"));
        c.setNombre(req.nombre().trim());
        c.setActiva(req.activa());
        try {
            categorias.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("Ya existe una categoría con ese nombre");
        }
        auditoria.registrar(id == null ? "Categoría de gasto creada" : "Categoría de gasto modificada",
                "CategoriaGasto", c.getId(), c.getNombre());
        return c;
    }
}
