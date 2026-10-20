package com.taqueria.web;

import com.taqueria.dto.Dtos.*;
import com.taqueria.model.Cliente;
import com.taqueria.model.Compra;
import com.taqueria.model.Proveedor;
import com.taqueria.service.ClienteService;
import com.taqueria.service.CompraService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Proveedores, compras y clientes. */
@RestController
@RequestMapping("/api")
public class CompraController {

    private final CompraService compras;
    private final ClienteService clientes;

    public CompraController(CompraService compras, ClienteService clientes) {
        this.compras = compras;
        this.clientes = clientes;
    }

    @GetMapping("/proveedores")
    @PreAuthorize("hasAnyAuthority('compras.ver', 'proveedores.gestionar', 'inventario.gestionar')")
    public List<Proveedor> proveedores() {
        return compras.proveedores();
    }

    @PostMapping("/proveedores")
    @PreAuthorize("hasAuthority('proveedores.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Proveedor crearProveedor(@Valid @RequestBody ProveedorRequest req) {
        return compras.guardarProveedor(null, req);
    }

    @PutMapping("/proveedores/{id}")
    @PreAuthorize("hasAuthority('proveedores.gestionar')")
    public Proveedor editarProveedor(@PathVariable Long id, @Valid @RequestBody ProveedorRequest req) {
        return compras.guardarProveedor(id, req);
    }

    @GetMapping("/compras")
    @PreAuthorize("hasAuthority('compras.ver')")
    public List<Compra> compras(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                @RequestParam(required = false) Long proveedorId) {
        return compras.buscar(desde, hasta, proveedorId);
    }

    @GetMapping("/compras/deudas")
    @PreAuthorize("hasAuthority('compras.ver')")
    public List<Map<String, Object>> deudas() {
        return compras.deudas();
    }

    @GetMapping("/compras/{id}")
    @PreAuthorize("hasAuthority('compras.ver')")
    public Compra compra(@PathVariable Long id) {
        return compras.obtener(id);
    }

    @PostMapping("/compras")
    @PreAuthorize("hasAuthority('compras.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Compra crearCompra(@Valid @RequestBody CompraRequest req) {
        return compras.crear(req);
    }

    @PostMapping("/compras/{id}/recibir")
    @PreAuthorize("hasAuthority('compras.recibir')")
    public Compra recibir(@PathVariable Long id) {
        return compras.recibir(id);
    }

    @PostMapping("/compras/{id}/pagos")
    @PreAuthorize("hasAuthority('compras.gestionar')")
    public Compra pagar(@PathVariable Long id, @Valid @RequestBody PagoCompraRequest req) {
        return compras.pagar(id, req);
    }

    @PostMapping("/compras/{id}/anular")
    @PreAuthorize("hasAuthority('compras.gestionar')")
    public Compra anular(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        return compras.anular(id, req.motivo());
    }

    // ---------- Clientes ----------

    @GetMapping("/clientes")
    @PreAuthorize("hasAuthority('clientes.ver')")
    public Map<String, Object> clientes(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) Boolean activo,
                                        @RequestParam(defaultValue = "0") int pagina,
                                        @RequestParam(defaultValue = "20") int tamano) {
        return ApiErrores.pagina(clientes.buscar(q, activo, pagina, tamano));
    }

    @PostMapping("/clientes")
    @PreAuthorize("hasAuthority('clientes.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Cliente crearCliente(@Valid @RequestBody ClienteRequest req) {
        return clientes.guardar(null, req);
    }

    @PutMapping("/clientes/{id}")
    @PreAuthorize("hasAuthority('clientes.gestionar')")
    public Cliente editarCliente(@PathVariable Long id, @Valid @RequestBody ClienteRequest req) {
        return clientes.guardar(id, req);
    }

    @GetMapping("/clientes/{id}/historial")
    @PreAuthorize("hasAuthority('clientes.ver')")
    public Map<String, Object> historial(@PathVariable Long id) {
        return clientes.historial(id);
    }
}
