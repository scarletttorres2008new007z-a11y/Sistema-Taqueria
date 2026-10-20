package com.taqueria.web;

import com.taqueria.dto.Dtos.ItemInventarioRequest;
import com.taqueria.dto.Dtos.MovimientoInventarioRequest;
import com.taqueria.model.*;
import com.taqueria.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class InventarioController {

    private final InventarioService inventario;

    public InventarioController(InventarioService inventario) {
        this.inventario = inventario;
    }

    @GetMapping("/unidades")
    public List<UnidadMedida> unidades() {
        return inventario.unidades();
    }

    @GetMapping("/inventario")
    @PreAuthorize("hasAnyAuthority('inventario.ver', 'recetas.gestionar', 'compras.ver', 'productos.gestionar')")
    public List<InventarioItem> items() {
        return inventario.listar();
    }

    @GetMapping("/inventario/{id}")
    @PreAuthorize("hasAuthority('inventario.ver')")
    public InventarioItem item(@PathVariable Long id) {
        return inventario.item(id);
    }

    @PostMapping("/inventario")
    @PreAuthorize("hasAuthority('inventario.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public InventarioItem crear(@Valid @RequestBody ItemInventarioRequest req) {
        return inventario.guardarItem(null, req);
    }

    @PutMapping("/inventario/{id}")
    @PreAuthorize("hasAuthority('inventario.gestionar')")
    public InventarioItem editar(@PathVariable Long id, @Valid @RequestBody ItemInventarioRequest req) {
        return inventario.guardarItem(id, req);
    }

    @GetMapping("/inventario/alertas")
    @PreAuthorize("hasAuthority('inventario.ver')")
    public Map<String, List<InventarioItem>> alertas() {
        return inventario.alertas();
    }

    @GetMapping("/inventario/movimientos")
    @PreAuthorize("hasAuthority('inventario.ver')")
    public Map<String, Object> movimientos(@RequestParam(required = false) Long itemId,
                                           @RequestParam(required = false) TipoMovimientoInventario tipo,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                           @RequestParam(defaultValue = "0") int pagina,
                                           @RequestParam(defaultValue = "25") int tamano) {
        return ApiErrores.pagina(inventario.buscarMovimientos(itemId, tipo, desde, hasta, pagina, tamano));
    }

    /** El permiso exacto (entradas, salidas o ajustes) se valida en el servicio segun el tipo. */
    @PostMapping("/inventario/movimientos")
    @PreAuthorize("hasAnyAuthority('inventario.entradas', 'inventario.salidas', 'inventario.ajustar')")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoInventario movimiento(@Valid @RequestBody MovimientoInventarioRequest req) {
        return inventario.movimientoManual(req);
    }
}
