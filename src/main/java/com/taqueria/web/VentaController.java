package com.taqueria.web;

import com.taqueria.dto.Dtos.*;
import com.taqueria.dto.Vistas.ComprobanteVista;
import com.taqueria.dto.Vistas.VentaVista;
import com.taqueria.model.Comprobante;
import com.taqueria.model.EstadoComprobante;
import com.taqueria.model.EstadoVenta;
import com.taqueria.security.IdempotenciaFilter;
import com.taqueria.service.ApiException;
import com.taqueria.service.ComprobanteService;
import com.taqueria.service.VentaService;
import com.taqueria.repository.ComprobanteRepository;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Ventas, pagos y comprobantes. */
@RestController
@RequestMapping("/api")
public class VentaController {

    private final VentaService ventas;
    private final ComprobanteService comprobantes;
    private final ComprobanteRepository comprobanteRepo;

    public VentaController(VentaService ventas, ComprobanteService comprobantes, ComprobanteRepository comprobanteRepo) {
        this.ventas = ventas;
        this.comprobantes = comprobantes;
        this.comprobanteRepo = comprobanteRepo;
    }

    @PostMapping("/ventas/directa")
    @PreAuthorize("hasAuthority('ventas.cobrar')")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaVista directa(@Valid @RequestBody VentaDirectaRequest req,
                              @RequestHeader(value = IdempotenciaFilter.CABECERA, required = false) String clave) {
        return ventas.ventaDirecta(req, clave);
    }

    @GetMapping("/ventas")
    @PreAuthorize("hasAuthority('ventas.ver')")
    public Map<String, Object> buscar(@RequestParam(required = false) String numero,
                                      @RequestParam(required = false) EstadoVenta estado,
                                      @RequestParam(required = false) Long usuarioId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "20") int tamano) {
        return ApiErrores.pagina(ventas.buscar(numero, estado, usuarioId, desde, hasta, pagina, tamano));
    }

    @GetMapping("/ventas/pendientes")
    @PreAuthorize("hasAuthority('ventas.ver')")
    public List<VentaVista> pendientes() {
        return ventas.pendientesDePago();
    }

    @GetMapping("/ventas/{id}")
    @PreAuthorize("hasAuthority('ventas.ver')")
    public VentaVista venta(@PathVariable Long id) {
        return ventas.vista(id);
    }

    @PostMapping("/ventas/{id}/pagos")
    @PreAuthorize("hasAuthority('ventas.cobrar')")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaVista pagar(@PathVariable Long id, @Valid @RequestBody PagoRequest req,
                            @RequestHeader(value = IdempotenciaFilter.CABECERA, required = false) String clave) {
        return ventas.registrarPago(id, req, clave);
    }

    @PostMapping("/ventas/{id}/anular")
    @PreAuthorize("hasAuthority('ventas.anular')")
    public VentaVista anular(@PathVariable Long id, @Valid @RequestBody AnulacionRequest req) {
        return ventas.anular(id, req);
    }

    // ---------- Comprobantes ----------

    @GetMapping("/comprobantes")
    @PreAuthorize("hasAuthority('comprobantes.ver')")
    public Map<String, Object> comprobantes(@RequestParam(required = false) String numero,
                                            @RequestParam(required = false) String cliente,
                                            @RequestParam(required = false) EstadoComprobante estado,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(defaultValue = "0") int pagina,
                                            @RequestParam(defaultValue = "20") int tamano) {
        return ApiErrores.pagina(comprobantes.buscarConVenta(numero, cliente, estado, desde, hasta, pagina, tamano));
    }

    @GetMapping("/comprobantes/{id}")
    @PreAuthorize("hasAuthority('comprobantes.ver')")
    @Transactional(readOnly = true)
    public ComprobanteVista comprobante(@PathVariable Long id) {
        Comprobante c = comprobantes.obtener(id);
        return comprobantes.vista(c, ventas.vista(c.getVentaId()));
    }

    @GetMapping("/comprobantes/venta/{ventaId}")
    @PreAuthorize("hasAuthority('comprobantes.ver')")
    @Transactional(readOnly = true)
    public ComprobanteVista comprobanteDeVenta(@PathVariable Long ventaId) {
        Comprobante c = comprobanteRepo.findByVentaId(ventaId).orElseThrow(() -> ApiException.noEncontrado("Comprobante"));
        return comprobantes.vista(c, ventas.vista(ventaId));
    }

    /** Registra que se imprimio (o reimprimio). No crea ventas ni comprobantes nuevos. */
    @PostMapping("/comprobantes/{id}/impresiones")
    @PreAuthorize("hasAuthority('comprobantes.imprimir')")
    public Map<String, Object> impresion(@PathVariable Long id) {
        return Map.of("impresiones", comprobantes.registrarImpresion(id));
    }
}
