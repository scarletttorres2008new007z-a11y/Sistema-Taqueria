package com.taqueria.service;

import com.taqueria.model.Caja;
import com.taqueria.model.InventarioItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Indicadores del panel principal, calculados con datos reales de la base. */
@Service
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final InventarioService inventario;
    private final CajaService caja;
    private final CompraService compras;

    public DashboardService(NamedParameterJdbcTemplate jdbc, InventarioService inventario, CajaService caja,
                            CompraService compras) {
        this.jdbc = jdbc;
        this.inventario = inventario;
        this.caja = caja;
        this.compras = compras;
    }

    public Map<String, Object> resumen(LocalDate desde, LocalDate hasta) {
        LocalDate d = Fechas.o(desde, Fechas.hoy());
        LocalDate h = Fechas.o(hasta, d);
        Fechas.validarRango(d, h);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", OffsetDateTime.ofInstant(Fechas.inicio(d), ZoneOffset.UTC))
                .addValue("hasta", OffsetDateTime.ofInstant(Fechas.finExclusivo(h), ZoneOffset.UTC))
                .addValue("fdesde", d).addValue("fhasta", h)
                .addValue("hoyDesde", OffsetDateTime.ofInstant(Fechas.inicio(Fechas.hoy()), ZoneOffset.UTC))
                .addValue("hoyHasta", OffsetDateTime.ofInstant(Fechas.finExclusivo(Fechas.hoy()), ZoneOffset.UTC));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("desde", d);
        r.put("hasta", h);

        Map<String, Object> ventas = jdbc.queryForMap("""
                SELECT count(*) AS cantidad,
                       coalesce(sum(subtotal), 0) AS ventas_brutas,
                       coalesce(sum(descuento), 0) AS descuentos,
                       coalesce(sum(total), 0) AS ventas_netas,
                       coalesce(sum(impuesto), 0) AS iva,
                       coalesce(sum(base_imponible), 0) AS ventas_sin_iva,
                       coalesce(sum(costo_estimado), 0) AS costos
                FROM ventas WHERE estado <> 'ANULADA' AND creado_en >= :desde AND creado_en < :hasta""", p);
        BigDecimal cobrado = jdbc.queryForObject("""
                SELECT coalesce(sum(monto), 0) FROM pagos
                WHERE estado = 'APLICADO' AND creado_en >= :desde AND creado_en < :hasta""", p, BigDecimal.class);
        BigDecimal gastos = jdbc.queryForObject("""
                SELECT coalesce(sum(monto), 0) FROM gastos WHERE NOT anulado AND fecha BETWEEN :fdesde AND :fhasta""",
                p, BigDecimal.class);
        BigDecimal sinIva = (BigDecimal) ventas.get("ventas_sin_iva");
        BigDecimal costos = (BigDecimal) ventas.get("costos");
        r.put("ventas", ventas);
        r.put("ingresosCobrados", cobrado);
        r.put("gastos", gastos);
        r.put("utilidadBruta", sinIva.subtract(costos));
        r.put("utilidadEstimada", sinIva.subtract(costos).subtract(gastos));

        Map<String, Object> hoy = jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM pedidos WHERE creado_en >= :hoyDesde AND creado_en < :hoyHasta AND estado <> 'CANCELADO') AS pedidos_hoy,
                       (SELECT coalesce(sum(total), 0) FROM ventas WHERE estado <> 'ANULADA' AND creado_en >= :hoyDesde AND creado_en < :hoyHasta) AS ventas_hoy,
                       (SELECT count(*) FROM pedidos WHERE estado = 'PENDIENTE') AS pendientes,
                       (SELECT count(*) FROM pedidos WHERE estado = 'EN_PREPARACION') AS en_preparacion,
                       (SELECT count(*) FROM pedidos WHERE estado = 'LISTO') AS listos""", p);
        r.put("hoy", hoy);
        r.put("pedidosPeriodo", jdbc.queryForObject("""
                SELECT count(*) FROM pedidos WHERE creado_en >= :desde AND creado_en < :hasta AND estado <> 'CANCELADO'""",
                p, Long.class));

        r.put("topProductos", jdbc.queryForList("""
                SELECT pr.nombre, sum(vd.cantidad) AS cantidad, sum(vd.subtotal) AS total
                FROM venta_detalles vd JOIN ventas v ON v.id = vd.venta_id JOIN productos pr ON pr.id = vd.producto_id
                WHERE v.estado <> 'ANULADA' AND v.creado_en >= :desde AND v.creado_en < :hasta
                GROUP BY pr.nombre ORDER BY cantidad DESC LIMIT 8""", p));

        r.put("ventasPorDia", jdbc.queryForList("""
                SELECT to_char(creado_en AT TIME ZONE 'America/El_Salvador', 'YYYY-MM-DD') AS dia,
                       sum(total) AS total, count(*) AS cantidad
                FROM ventas WHERE estado <> 'ANULADA' AND creado_en >= :desde AND creado_en < :hasta
                GROUP BY 1 ORDER BY 1""", p));

        Map<String, List<InventarioItem>> alertas = inventario.alertas();
        List<Map<String, Object>> bajas = new ArrayList<>();
        alertas.get("agotados").forEach(i -> bajas.add(item(i, "Agotado")));
        alertas.get("bajos").forEach(i -> bajas.add(item(i, "Bajo")));
        r.put("existenciasBajas", bajas);
        List<Map<String, Object>> vencer = new ArrayList<>();
        alertas.get("vencidos").forEach(i -> vencer.add(item(i, "Vencido")));
        alertas.get("porVencer").forEach(i -> vencer.add(item(i, "Por vencer")));
        r.put("porVencer", vencer);

        r.put("ventasPendientes", jdbc.queryForMap("""
                SELECT count(*) AS cantidad, coalesce(sum(total - monto_pagado), 0) AS saldo
                FROM ventas WHERE estado = 'PENDIENTE_PAGO'""", p));
        List<Map<String, Object>> deudas = compras.deudas();
        BigDecimal deudaTotal = deudas.stream().map(m -> (BigDecimal) m.get("saldo")).reduce(BigDecimal.ZERO, BigDecimal::add);
        int vencidas = deudas.stream().mapToInt(m -> (int) m.get("vencidas")).sum();
        r.put("deudasProveedores", Map.of("saldo", deudaTotal, "proveedores", deudas.size(), "vencidas", vencidas));

        Optional<Caja> abierta = caja.actual();
        if (abierta.isPresent()) {
            var res = caja.resumen(abierta.get());
            r.put("caja", Map.of("abierta", true, "id", abierta.get().getId(), "abiertaEn", abierta.get().getAbiertaEn(),
                    "abiertaPor", Objects.toString(res.abiertaPor(), ""), "efectivoEsperado", res.efectivoEsperado(),
                    "ventasTarjeta", res.ventasTarjeta(), "ventasTransferencia", res.ventasTransferencia()));
        } else {
            r.put("caja", Map.of("abierta", false));
        }
        return r;
    }

    private static Map<String, Object> item(InventarioItem i, String estado) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("nombre", i.getNombre());
        m.put("existencia", i.getExistencia());
        m.put("minimo", i.getExistenciaMinima());
        m.put("unidad", i.getUnidad().getCodigo());
        m.put("fechaVencimiento", i.getFechaVencimiento());
        m.put("estado", estado);
        return m;
    }
}
