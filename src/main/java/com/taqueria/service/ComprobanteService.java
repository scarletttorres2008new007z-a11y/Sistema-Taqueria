package com.taqueria.service;

import com.taqueria.dto.Vistas.ComprobanteVista;
import com.taqueria.model.*;
import com.taqueria.repository.ComprobanteRepository;
import com.taqueria.repository.VentaRepository;
import com.taqueria.security.UsuarioActual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comprobantes internos de venta. Cada venta tiene uno solo (indice unico), y volver a
 * imprimirlo solo incrementa un contador: nunca crea otra venta ni otro documento.
 */
@Service
public class ComprobanteService {

    public static final String LEYENDA = "COMPROBANTE INTERNO - NO ES UN DOCUMENTO TRIBUTARIO ELECTRÓNICO "
            + "NI SUSTITUYE A LA FACTURA AUTORIZADA POR EL MINISTERIO DE HACIENDA";

    private final ComprobanteRepository comprobantes;
    private final ConfiguracionService configuracion;
    private final UsuarioService usuarios;
    private final AuditoriaService auditoria;
    private final VentaRepository ventas;

    public ComprobanteService(ComprobanteRepository comprobantes, ConfiguracionService configuracion,
                              UsuarioService usuarios, AuditoriaService auditoria, VentaRepository ventas) {
        this.ventas = ventas;
        this.comprobantes = comprobantes;
        this.configuracion = configuracion;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    /** Emite el comprobante de una venta; si ya existe devuelve el mismo. */
    @Transactional
    public Comprobante emitir(Venta venta) {
        return comprobantes.findByVentaId(venta.getId()).orElseGet(() -> {
            Configuracion cfg = configuracion.obtener();
            Comprobante c = new Comprobante();
            c.setNumero("TMP-" + UUID.randomUUID().toString().substring(0, 12));
            c.setVentaId(venta.getId());
            c.setNegocioNombre(cfg.getNombreComercial());
            c.setNegocioDireccion(cfg.getDireccion());
            c.setNegocioTelefono(cfg.getTelefono());
            c.setNegocioNit(cfg.getNit());
            c.setNegocioNrc(cfg.getNrc());
            c.setMensaje(cfg.getMensajeComprobante());
            c.setClienteNombre(venta.getClienteNombre());
            c.setClienteDocumento(venta.getClienteDocumento());
            c.setClienteDireccion(venta.getClienteDireccion());
            c.setUsuarioId(UsuarioActual.id());
            comprobantes.saveAndFlush(c);
            c.setNumero(String.format("CI-%08d", c.getId()));
            comprobantes.save(c);
            auditoria.registrar("Comprobante emitido", "Comprobante", c.getId(), c.getNumero() + " de la venta " + venta.getNumero());
            return c;
        });
    }

    public Comprobante obtener(Long id) {
        return comprobantes.findById(id).orElseThrow(() -> ApiException.noEncontrado("Comprobante"));
    }

    @Transactional
    public void anular(Long ventaId) {
        comprobantes.findByVentaId(ventaId).ifPresent(c -> {
            c.setEstado(EstadoComprobante.ANULADO);
            comprobantes.save(c);
        });
    }

    /** Registra una impresion o reimpresion sin crear documentos nuevos. */
    @Transactional
    public int registrarImpresion(Long id) {
        Comprobante c = obtener(id);
        comprobantes.registrarImpresion(id, Instant.now());
        int numero = c.getImpresiones() + 1;
        auditoria.registrar(numero == 1 ? "Comprobante impreso" : "Comprobante reimpreso", "Comprobante", id,
                c.getNumero() + " (impresión #" + numero + ")");
        return numero;
    }

    public Page<Comprobante> buscar(String numero, String cliente, EstadoComprobante estado, LocalDate desde,
                                    LocalDate hasta, int pagina, int tamano) {
        // Al buscar por numero se revisa todo el historial (hasta 3 años) si no se indica fecha
        LocalDate d = Fechas.o(desde, Texto.vacio(numero) ? Fechas.hoy().minusDays(30) : Fechas.hoy().minusYears(3));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        return comprobantes.buscar(Texto.limpio(numero), Texto.limpio(cliente), estado, Fechas.inicio(d),
                Fechas.finExclusivo(h), PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100)));
    }

    /** Lista de comprobantes con el numero, total y estado de su venta. */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> buscarConVenta(String numero, String cliente, EstadoComprobante estado, LocalDate desde,
                                                    LocalDate hasta, int pagina, int tamano) {
        Page<Comprobante> p;
        String n = numero == null ? null : numero.trim().toUpperCase();
        if (n != null && n.matches("V-?\\d{1,12}")) {
            // Busqueda por numero de venta (V-000123): el comprobante de esa venta
            List<Comprobante> lista = comprobantes.findByVentaId(Long.valueOf(n.replaceAll("\\D", ""))).stream().toList();
            p = new PageImpl<>(lista, PageRequest.of(0, Math.max(1, tamano)), lista.size());
        } else {
            p = buscar(numero, cliente, estado, desde, hasta, pagina, tamano);
        }
        Map<Long, Venta> porId = ventas.findAllById(p.map(Comprobante::getVentaId).toList()).stream()
                .collect(Collectors.toMap(Venta::getId, Function.identity()));
        return p.map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Venta v = porId.get(c.getVentaId());
            m.put("id", c.getId());
            m.put("numero", c.getNumero());
            m.put("fechaEmision", c.getFechaEmision());
            m.put("clienteNombre", c.getClienteNombre());
            m.put("estado", c.getEstado());
            m.put("impresiones", c.getImpresiones());
            m.put("ultimaImpresion", c.getUltimaImpresion());
            m.put("ventaId", c.getVentaId());
            m.put("ventaNumero", v == null ? null : v.getNumero());
            m.put("ventaEstado", v == null ? null : v.getEstado());
            m.put("total", v == null ? null : v.getTotal());
            return m;
        });
    }

    public ComprobanteVista vista(Comprobante c, com.taqueria.dto.Vistas.VentaVista venta) {
        Configuracion cfg = configuracion.obtener();
        return new ComprobanteVista(c, venta, venta.ivaPorcentaje(), cfg.getAnchoPapelMm(), cfg.getLogoUrl(),
                usuarios.nombres().get(c.getUsuarioId()), LEYENDA);
    }
}
