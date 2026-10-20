package com.taqueria.web;

import com.taqueria.service.DashboardService;
import com.taqueria.service.ReporteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReporteController {

    private final ReporteService reportes;
    private final DashboardService dashboard;

    public ReporteController(ReporteService reportes, DashboardService dashboard) {
        this.reportes = reportes;
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('dashboard.ver')")
    public Map<String, Object> dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboard.resumen(desde, hasta);
    }

    @GetMapping("/reportes")
    @PreAuthorize("hasAuthority('reportes.ver')")
    public Map<String, String> tipos() {
        return ReporteService.TIPOS;
    }

    @GetMapping("/reportes/{tipo}")
    @PreAuthorize("hasAuthority('reportes.ver')")
    public ReporteService.Reporte reporte(@PathVariable String tipo,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                          @RequestParam(required = false) String agrupacion) {
        return reportes.generar(tipo, desde, hasta, agrupacion);
    }

    @GetMapping("/reportes/{tipo}/csv")
    @PreAuthorize("hasAuthority('reportes.ver')")
    public ResponseEntity<byte[]> csv(@PathVariable String tipo,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(required = false) String agrupacion) {
        ReporteService.Reporte r = reportes.generar(tipo, desde, hasta, agrupacion);
        String nombre = "reporte-" + tipo + "-" + r.desde() + "-a-" + r.hasta() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(reportes.aCsv(r).getBytes(StandardCharsets.UTF_8));
    }
}
