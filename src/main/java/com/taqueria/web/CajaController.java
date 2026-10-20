package com.taqueria.web;

import com.taqueria.dto.Dtos.*;
import com.taqueria.dto.Vistas.CajaResumen;
import com.taqueria.model.CategoriaGasto;
import com.taqueria.model.Gasto;
import com.taqueria.model.MovimientoCaja;
import com.taqueria.service.CajaService;
import com.taqueria.service.GastoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Caja y gastos. */
@RestController
@RequestMapping("/api")
public class CajaController {

    private final CajaService caja;
    private final GastoService gastos;

    public CajaController(CajaService caja, GastoService gastos) {
        this.caja = caja;
        this.gastos = gastos;
    }

    @GetMapping("/caja/actual")
    @PreAuthorize("hasAnyAuthority('caja.ver', 'ventas.cobrar')")
    public Map<String, Object> actual() {
        Map<String, Object> m = new LinkedHashMap<>();
        caja.actual().ifPresentOrElse(c -> {
            m.put("abierta", true);
            m.put("resumen", caja.resumen(c));
        }, () -> m.put("abierta", false));
        return m;
    }

    @PostMapping("/caja/abrir")
    @PreAuthorize("hasAuthority('caja.operar')")
    @ResponseStatus(HttpStatus.CREATED)
    public CajaResumen abrir(@Valid @RequestBody AperturaCajaRequest req) {
        return caja.resumen(caja.abrir(req));
    }

    @PostMapping("/caja/cerrar")
    @PreAuthorize("hasAuthority('caja.operar')")
    public CajaResumen cerrar(@Valid @RequestBody CierreCajaRequest req) {
        return caja.cerrar(req);
    }

    @PostMapping("/caja/movimientos")
    @PreAuthorize("hasAuthority('caja.operar')")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoCaja movimiento(@Valid @RequestBody MovimientoCajaRequest req) {
        return caja.movimientoManual(req);
    }

    @GetMapping("/caja/cierres")
    @PreAuthorize("hasAuthority('caja.ver')")
    public Map<String, Object> cierres(@RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "15") int tamano) {
        return ApiErrores.pagina(caja.cierres(pagina, tamano));
    }

    @GetMapping("/caja/{id}")
    @PreAuthorize("hasAuthority('caja.ver')")
    public CajaResumen detalle(@PathVariable Long id) {
        return caja.resumen(caja.obtener(id));
    }

    // ---------- Gastos ----------

    @GetMapping("/gastos")
    @PreAuthorize("hasAuthority('gastos.ver')")
    public List<Gasto> gastos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                              @RequestParam(required = false) Long categoriaId) {
        return gastos.buscar(desde, hasta, categoriaId);
    }

    @PostMapping("/gastos")
    @PreAuthorize("hasAuthority('gastos.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Gasto crearGasto(@Valid @RequestBody GastoRequest req) {
        return gastos.crear(req);
    }

    @PostMapping("/gastos/{id}/anular")
    @PreAuthorize("hasAuthority('gastos.gestionar')")
    public Gasto anularGasto(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return gastos.anular(id, req.motivo());
    }

    @PostMapping("/gastos/{id}/comprobante")
    @PreAuthorize("hasAuthority('gastos.gestionar')")
    public Gasto comprobanteGasto(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return gastos.adjuntarComprobante(id, archivo);
    }

    @GetMapping("/categorias-gasto")
    @PreAuthorize("hasAuthority('gastos.ver')")
    public List<CategoriaGasto> categoriasGasto() {
        return gastos.categorias();
    }

    @PostMapping("/categorias-gasto")
    @PreAuthorize("hasAuthority('configuracion.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaGasto crearCategoriaGasto(@Valid @RequestBody CategoriaGastoRequest req) {
        return gastos.guardarCategoria(null, req);
    }

    @PutMapping("/categorias-gasto/{id}")
    @PreAuthorize("hasAuthority('configuracion.gestionar')")
    public CategoriaGasto editarCategoriaGasto(@PathVariable Long id, @Valid @RequestBody CategoriaGastoRequest req) {
        return gastos.guardarCategoria(id, req);
    }
}
