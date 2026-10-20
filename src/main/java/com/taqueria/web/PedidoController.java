package com.taqueria.web;

import com.taqueria.dto.Dtos.*;
import com.taqueria.dto.Vistas.HistorialEstado;
import com.taqueria.dto.Vistas.PedidoVista;
import com.taqueria.dto.Vistas.VentaVista;
import com.taqueria.model.EstadoPedido;
import com.taqueria.model.TipoPedido;
import com.taqueria.security.IdempotenciaFilter;
import com.taqueria.service.PedidoEventos;
import com.taqueria.service.PedidoService;
import com.taqueria.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidos;
    private final VentaService ventas;
    private final PedidoEventos eventos;

    public PedidoController(PedidoService pedidos, VentaService ventas, PedidoEventos eventos) {
        this.pedidos = pedidos;
        this.ventas = ventas;
        this.eventos = eventos;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pedidos.crear')")
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoVista crear(@Valid @RequestBody PedidoRequest req,
                             @RequestHeader(value = IdempotenciaFilter.CABECERA, required = false) String clave) {
        return pedidos.vista(pedidos.crear(req, clave).getId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pedidos.ver')")
    public Map<String, Object> buscar(@RequestParam(required = false) String numero,
                                      @RequestParam(required = false) EstadoPedido estado,
                                      @RequestParam(required = false) TipoPedido tipo,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                      @RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "20") int tamano) {
        return ApiErrores.pagina(pedidos.buscar(numero, estado, tipo, desde, hasta, pagina, tamano));
    }

    @GetMapping("/cocina")
    @PreAuthorize("hasAuthority('pedidos.ver')")
    public List<PedidoVista> cocina() {
        return pedidos.cocina();
    }

    /** Eventos en tiempo real: el navegador se suscribe y recibe un aviso por cada cambio. */
    @GetMapping(value = "/eventos", produces = "text/event-stream")
    @PreAuthorize("hasAuthority('pedidos.ver')")
    public SseEmitter eventos() {
        return eventos.suscribir();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pedidos.ver')")
    public PedidoVista obtener(@PathVariable Long id) {
        return pedidos.vista(id);
    }

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('pedidos.ver')")
    public List<HistorialEstado> historial(@PathVariable Long id) {
        return pedidos.historial(id);
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('pedidos.estado')")
    public PedidoVista estado(@PathVariable Long id, @Valid @RequestBody EstadoPedidoRequest req) {
        pedidos.cambiarEstado(id, req.estado(), req.motivo());
        return pedidos.vista(id);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('pedidos.cancelar')")
    public PedidoVista cancelar(@PathVariable Long id, @Valid @RequestBody MotivoRequest req) {
        pedidos.cancelar(id, req.motivo().trim());
        return pedidos.vista(id);
    }

    /** Registra la venta del pedido (y opcionalmente sus pagos) y emite el comprobante. */
    @PostMapping("/{id}/venta")
    @PreAuthorize("hasAuthority('ventas.cobrar')")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaVista cobrar(@PathVariable Long id, @Valid @RequestBody CobroRequest req,
                             @RequestHeader(value = IdempotenciaFilter.CABECERA, required = false) String clave) {
        return ventas.cobrarPedido(id, req, clave);
    }
}
