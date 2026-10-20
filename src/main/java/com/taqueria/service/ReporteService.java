package com.taqueria.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Reportes calculados directamente en la base de datos. Cada reporte explica de donde
 * salen sus datos y como se calculan, y se puede exportar a CSV o PDF.
 */
@Service
public class ReporteService {

    /** Columna de un reporte: tipo = texto, numero, dinero, fecha o fechahora. */
    public record Columna(String clave, String titulo, String tipo) {
    }

    public record Reporte(String tipo, String titulo, String explicacion, LocalDate desde, LocalDate hasta,
                          List<Columna> columnas, List<Map<String, Object>> filas, Map<String, Object> totales) {
    }

    public static final Map<String, String> TIPOS = new LinkedHashMap<>();

    static {
        TIPOS.put("ventas", "Ventas por período");
        TIPOS.put("utilidad", "Utilidad estimada por período");
        TIPOS.put("productos", "Productos más vendidos");
        TIPOS.put("metodos-pago", "Ingresos por método de pago");
        TIPOS.put("gastos", "Gastos por categoría");
        TIPOS.put("compras-proveedor", "Compras por proveedor");
        TIPOS.put("movimientos-inventario", "Movimientos de inventario");
        TIPOS.put("existencias-bajas", "Ingredientes con existencias bajas");
        TIPOS.put("pedidos-estado", "Pedidos por estado");
        TIPOS.put("anulaciones", "Anulaciones y devoluciones");
        TIPOS.put("cierres-caja", "Cierres de caja");
    }

    private static final String LOCAL_VENTA = "(v.creado_en AT TIME ZONE 'America/El_Salvador')";

    private final NamedParameterJdbcTemplate jdbc;

    public ReporteService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Reporte generar(String tipo, LocalDate desde, LocalDate hasta, String agrupacion) {
        LocalDate d = Fechas.o(desde, Fechas.hoy().withDayOfMonth(1));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("desde", OffsetDateTime.ofInstant(Fechas.inicio(d), ZoneOffset.UTC))
                .addValue("hasta", OffsetDateTime.ofInstant(Fechas.finExclusivo(h), ZoneOffset.UTC))
                .addValue("fdesde", d)
                .addValue("fhasta", h);
        String grupo = agrupacion == null ? "DIA" : agrupacion.toUpperCase();
        return switch (tipo) {
            case "ventas" -> ventas(d, h, grupo, p);
            case "utilidad" -> utilidad(d, h, grupo, p);
            case "productos" -> productos(d, h, p);
            case "metodos-pago" -> metodosPago(d, h, p);
            case "gastos" -> gastos(d, h, p);
            case "compras-proveedor" -> comprasProveedor(d, h, p);
            case "movimientos-inventario" -> movimientosInventario(d, h, p);
            case "existencias-bajas" -> existenciasBajas(d, h);
            case "pedidos-estado" -> pedidosEstado(d, h, p);
            case "anulaciones" -> anulaciones(d, h, p);
            case "cierres-caja" -> cierresCaja(d, h, p);
            default -> throw ApiException.noEncontrado("Reporte");
        };
    }

    private static String periodo(String campoLocal, String grupo) {
        return switch (grupo) {
            case "SEMANA" -> "to_char(date_trunc('week', " + campoLocal + "), 'YYYY-MM-DD')";
            case "MES" -> "to_char(" + campoLocal + ", 'YYYY-MM')";
            default -> "to_char(" + campoLocal + ", 'YYYY-MM-DD')";
        };
    }

    private static String nombreGrupo(String grupo) {
        return switch (grupo) {
            case "SEMANA" -> "Semana (inicia el lunes)";
            case "MES" -> "Mes";
            default -> "Día";
        };
    }

    private Reporte ventas(LocalDate d, LocalDate h, String grupo, MapSqlParameterSource p) {
        String per = periodo(LOCAL_VENTA, grupo);
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT %s AS periodo,
                       count(*) FILTER (WHERE v.estado <> 'ANULADA') AS ventas,
                       coalesce(sum(v.subtotal) FILTER (WHERE v.estado <> 'ANULADA'), 0) AS ventas_brutas,
                       coalesce(sum(v.descuento) FILTER (WHERE v.estado <> 'ANULADA'), 0) AS descuentos,
                       coalesce(sum(v.total) FILTER (WHERE v.estado <> 'ANULADA'), 0) AS ventas_netas,
                       coalesce(sum(v.impuesto) FILTER (WHERE v.estado <> 'ANULADA'), 0) AS iva,
                       coalesce(sum(v.monto_pagado) FILTER (WHERE v.estado <> 'ANULADA'), 0) AS cobrado,
                       coalesce(sum(v.total - v.monto_pagado) FILTER (WHERE v.estado = 'PENDIENTE_PAGO'), 0) AS por_cobrar,
                       count(*) FILTER (WHERE v.estado = 'ANULADA') AS anuladas
                FROM ventas v
                WHERE v.creado_en >= :desde AND v.creado_en < :hasta
                GROUP BY 1 ORDER BY 1""".formatted(per), p);
        return new Reporte("ventas", TIPOS.get("ventas"),
                "Ventas registradas por " + nombreGrupo(grupo).toLowerCase() + ". Ventas brutas = suma de productos antes de descuentos. "
                        + "Ventas netas = brutas − descuentos (IVA incluido). Cobrado = pagos recibidos de esas ventas. "
                        + "Las ventas anuladas no se suman, solo se cuentan.",
                d, h, List.of(new Columna("periodo", nombreGrupo(grupo), "texto"),
                new Columna("ventas", "N.º ventas", "numero"), new Columna("ventas_brutas", "Ventas brutas", "dinero"),
                new Columna("descuentos", "Descuentos", "dinero"), new Columna("ventas_netas", "Ventas netas", "dinero"),
                new Columna("iva", "IVA incluido", "dinero"), new Columna("cobrado", "Cobrado", "dinero"),
                new Columna("por_cobrar", "Por cobrar", "dinero"), new Columna("anuladas", "Anuladas", "numero")),
                filas, totales(filas, "ventas", "ventas_brutas", "descuentos", "ventas_netas", "iva", "cobrado", "por_cobrar", "anuladas"));
    }

    private Reporte utilidad(LocalDate d, LocalDate h, String grupo, MapSqlParameterSource p) {
        String perV = periodo(LOCAL_VENTA, grupo);
        String perG = periodo("g.fecha::timestamp", grupo);
        List<Map<String, Object>> filas = jdbc.queryForList("""
                WITH v AS (
                    SELECT %s AS periodo, sum(v.total) AS ventas_netas, sum(v.base_imponible) AS ventas_sin_iva,
                           sum(v.costo_estimado) AS costo
                    FROM ventas v WHERE v.estado <> 'ANULADA' AND v.creado_en >= :desde AND v.creado_en < :hasta
                    GROUP BY 1),
                g AS (
                    SELECT %s AS periodo, sum(g.monto) AS gastos
                    FROM gastos g WHERE NOT g.anulado AND g.fecha BETWEEN :fdesde AND :fhasta GROUP BY 1)
                SELECT coalesce(v.periodo, g.periodo) AS periodo,
                       coalesce(v.ventas_netas, 0) AS ventas_netas,
                       coalesce(v.ventas_sin_iva, 0) AS ventas_sin_iva,
                       coalesce(v.costo, 0) AS costo,
                       coalesce(v.ventas_sin_iva, 0) - coalesce(v.costo, 0) AS utilidad_bruta,
                       coalesce(g.gastos, 0) AS gastos,
                       coalesce(v.ventas_sin_iva, 0) - coalesce(v.costo, 0) - coalesce(g.gastos, 0) AS utilidad_estimada
                FROM v FULL OUTER JOIN g ON g.periodo = v.periodo
                ORDER BY 1""".formatted(perV, perG), p);
        return new Reporte("utilidad", TIPOS.get("utilidad"),
                "Utilidad estimada = ventas sin IVA − costo estimado de lo vendido − gastos operativos. "
                        + "El costo sale de la receta de cada producto (o del costo estimado configurado) al momento de vender. "
                        + "Las compras de inventario no se restan aquí para no contarlas dos veces: su costo entra al venderse. "
                        + "Es una estimación de gestión, no un estado financiero contable.",
                d, h, List.of(new Columna("periodo", nombreGrupo(grupo), "texto"),
                new Columna("ventas_netas", "Ventas (con IVA)", "dinero"), new Columna("ventas_sin_iva", "Ventas sin IVA", "dinero"),
                new Columna("costo", "Costo estimado", "dinero"), new Columna("utilidad_bruta", "Utilidad bruta", "dinero"),
                new Columna("gastos", "Gastos", "dinero"), new Columna("utilidad_estimada", "Utilidad estimada", "dinero")),
                filas, totales(filas, "ventas_netas", "ventas_sin_iva", "costo", "utilidad_bruta", "gastos", "utilidad_estimada"));
    }

    private Reporte productos(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT pr.codigo, pr.nombre, c.nombre AS categoria, sum(vd.cantidad) AS cantidad,
                       sum(vd.subtotal) AS total, sum(vd.costo_unitario * vd.cantidad) AS costo
                FROM venta_detalles vd
                JOIN ventas v ON v.id = vd.venta_id
                JOIN productos pr ON pr.id = vd.producto_id
                JOIN categorias c ON c.id = pr.categoria_id
                WHERE v.estado <> 'ANULADA' AND v.creado_en >= :desde AND v.creado_en < :hasta
                GROUP BY pr.codigo, pr.nombre, c.nombre
                ORDER BY cantidad DESC, total DESC""", p);
        return new Reporte("productos", TIPOS.get("productos"),
                "Cantidad vendida e importe por producto (antes de descuentos generales de la venta), sin ventas anuladas.",
                d, h, List.of(new Columna("codigo", "Código", "texto"), new Columna("nombre", "Producto", "texto"),
                new Columna("categoria", "Categoría", "texto"), new Columna("cantidad", "Cantidad", "numero"),
                new Columna("total", "Importe", "dinero"), new Columna("costo", "Costo estimado", "dinero")),
                filas, totales(filas, "cantidad", "total", "costo"));
    }

    private Reporte metodosPago(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT pg.metodo, count(*) FILTER (WHERE pg.estado = 'APLICADO') AS pagos,
                       coalesce(sum(pg.monto) FILTER (WHERE pg.estado = 'APLICADO'), 0) AS cobrado,
                       coalesce(sum(pg.monto) FILTER (WHERE pg.estado = 'REVERTIDO'), 0) AS devuelto
                FROM pagos pg WHERE pg.creado_en >= :desde AND pg.creado_en < :hasta
                GROUP BY pg.metodo ORDER BY cobrado DESC""", p);
        return new Reporte("metodos-pago", TIPOS.get("metodos-pago"),
                "Dinero efectivamente cobrado según la fecha del pago. Los pagos devueltos por anulación se muestran aparte.",
                d, h, List.of(new Columna("metodo", "Método", "texto"), new Columna("pagos", "N.º pagos", "numero"),
                new Columna("cobrado", "Cobrado", "dinero"), new Columna("devuelto", "Devuelto", "dinero")),
                filas, totales(filas, "pagos", "cobrado", "devuelto"));
    }

    private Reporte gastos(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT c.nombre AS categoria, count(*) AS cantidad, sum(g.monto) AS total,
                       coalesce(sum(g.monto) FILTER (WHERE g.pagado_desde_caja), 0) AS desde_caja
                FROM gastos g JOIN categorias_gasto c ON c.id = g.categoria_id
                WHERE NOT g.anulado AND g.fecha BETWEEN :fdesde AND :fhasta
                GROUP BY c.nombre ORDER BY total DESC""", p);
        return new Reporte("gastos", TIPOS.get("gastos"),
                "Gastos operativos registrados (sin anulados) por categoría. No incluye compras de inventario.",
                d, h, List.of(new Columna("categoria", "Categoría", "texto"), new Columna("cantidad", "N.º gastos", "numero"),
                new Columna("total", "Total", "dinero"), new Columna("desde_caja", "Pagado desde caja", "dinero")),
                filas, totales(filas, "cantidad", "total", "desde_caja"));
    }

    private Reporte comprasProveedor(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT pr.nombre_comercial AS proveedor, count(*) AS compras, sum(c.total) AS total,
                       sum(c.monto_pagado) AS pagado, sum(c.total - c.monto_pagado) AS saldo,
                       count(*) FILTER (WHERE c.estado = 'PENDIENTE_RECEPCION') AS por_recibir
                FROM compras c JOIN proveedores pr ON pr.id = c.proveedor_id
                WHERE c.estado <> 'ANULADA' AND c.fecha BETWEEN :fdesde AND :fhasta
                GROUP BY pr.nombre_comercial ORDER BY total DESC""", p);
        return new Reporte("compras-proveedor", TIPOS.get("compras-proveedor"),
                "Compras registradas por fecha de compra (sin anuladas), con lo pagado y el saldo pendiente.",
                d, h, List.of(new Columna("proveedor", "Proveedor", "texto"), new Columna("compras", "N.º compras", "numero"),
                new Columna("total", "Total", "dinero"), new Columna("pagado", "Pagado", "dinero"),
                new Columna("saldo", "Saldo", "dinero"), new Columna("por_recibir", "Por recibir", "numero")),
                filas, totales(filas, "compras", "total", "pagado", "saldo", "por_recibir"));
    }

    private Reporte movimientosInventario(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT i.codigo, i.nombre, u.codigo AS unidad,
                       coalesce(sum(m.cantidad) FILTER (WHERE m.tipo IN ('ENTRADA', 'COMPRA')), 0) AS entradas,
                       coalesce(-sum(m.cantidad) FILTER (WHERE m.tipo = 'VENTA'), 0) AS consumo_ventas,
                       coalesce(sum(m.cantidad) FILTER (WHERE m.tipo = 'DEVOLUCION_VENTA'), 0) AS devoluciones,
                       coalesce(-sum(m.cantidad) FILTER (WHERE m.tipo = 'SALIDA'), 0) AS salidas,
                       coalesce(sum(m.cantidad) FILTER (WHERE m.tipo = 'AJUSTE'), 0) AS ajustes,
                       coalesce(-sum(m.cantidad * coalesce(m.costo_unitario, 0)) FILTER (WHERE m.tipo = 'VENTA'), 0) AS costo_consumo,
                       i.existencia AS existencia_actual
                FROM movimientos_inventario m
                JOIN inventario_items i ON i.id = m.item_id
                JOIN unidades_medida u ON u.id = i.unidad_id
                WHERE m.creado_en >= :desde AND m.creado_en < :hasta
                GROUP BY i.codigo, i.nombre, u.codigo, i.existencia
                ORDER BY consumo_ventas DESC, i.nombre""", p);
        return new Reporte("movimientos-inventario", TIPOS.get("movimientos-inventario"),
                "Resumen por artículo de los movimientos del período. Consumo por ventas = lo descontado por recetas; "
                        + "los ajustes pueden ser positivos o negativos.",
                d, h, List.of(new Columna("codigo", "Código", "texto"), new Columna("nombre", "Artículo", "texto"),
                new Columna("unidad", "Unidad", "texto"), new Columna("entradas", "Entradas y compras", "numero"),
                new Columna("consumo_ventas", "Consumo por ventas", "numero"), new Columna("devoluciones", "Devoluciones", "numero"),
                new Columna("salidas", "Salidas", "numero"), new Columna("ajustes", "Ajustes", "numero"),
                new Columna("costo_consumo", "Costo del consumo", "dinero"), new Columna("existencia_actual", "Existencia actual", "numero")),
                filas, totales(filas, "costo_consumo"));
    }

    private Reporte existenciasBajas(LocalDate d, LocalDate h) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT i.codigo, i.nombre, i.categoria, u.codigo AS unidad, i.existencia, i.existencia_minima,
                       CASE WHEN i.existencia <= 0 THEN 'Agotado' ELSE 'Bajo' END AS estado,
                       pr.nombre_comercial AS proveedor
                FROM inventario_items i
                JOIN unidades_medida u ON u.id = i.unidad_id
                LEFT JOIN proveedores pr ON pr.id = i.proveedor_id
                WHERE i.activo AND i.existencia <= i.existencia_minima
                ORDER BY i.existencia <= 0 DESC, i.nombre""", new MapSqlParameterSource());
        return new Reporte("existencias-bajas", TIPOS.get("existencias-bajas"),
                "Artículos activos cuya existencia actual es igual o menor que la existencia mínima configurada (no depende de las fechas).",
                d, h, List.of(new Columna("codigo", "Código", "texto"), new Columna("nombre", "Artículo", "texto"),
                new Columna("categoria", "Categoría", "texto"), new Columna("unidad", "Unidad", "texto"),
                new Columna("existencia", "Existencia", "numero"), new Columna("existencia_minima", "Mínimo", "numero"),
                new Columna("estado", "Estado", "texto"), new Columna("proveedor", "Proveedor habitual", "texto")),
                filas, Map.of());
    }

    private Reporte pedidosEstado(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT pe.estado, pe.tipo, count(*) AS pedidos, sum(pe.total) AS total
                FROM pedidos pe WHERE pe.creado_en >= :desde AND pe.creado_en < :hasta
                GROUP BY pe.estado, pe.tipo ORDER BY pe.estado, pe.tipo""", p);
        return new Reporte("pedidos-estado", TIPOS.get("pedidos-estado"),
                "Pedidos registrados en el período según su estado actual y tipo (local, para llevar o domicilio).",
                d, h, List.of(new Columna("estado", "Estado", "texto"), new Columna("tipo", "Tipo", "texto"),
                new Columna("pedidos", "Pedidos", "numero"), new Columna("total", "Importe", "dinero")),
                filas, totales(filas, "pedidos", "total"));
    }

    private Reporte anulaciones(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT 'V-' || lpad(v.id::text, 6, '0') AS venta, v.anulada_en, v.total, v.motivo_anulacion AS motivo,
                       u.nombre AS anulada_por,
                       CASE WHEN v.inventario_devuelto THEN 'Sí' ELSE 'No' END AS inventario_devuelto,
                       coalesce((SELECT sum(pg.monto) FROM pagos pg WHERE pg.venta_id = v.id AND pg.estado = 'REVERTIDO'), 0) AS dinero_devuelto
                FROM ventas v LEFT JOIN usuarios u ON u.id = v.anulada_por
                WHERE v.estado = 'ANULADA' AND v.anulada_en >= :desde AND v.anulada_en < :hasta
                ORDER BY v.anulada_en DESC""", p);
        return new Reporte("anulaciones", TIPOS.get("anulaciones"),
                "Ventas anuladas en el período, quién las autorizó, el motivo, el dinero devuelto y si el inventario se devolvió.",
                d, h, List.of(new Columna("venta", "Venta", "texto"), new Columna("anulada_en", "Fecha", "fechahora"),
                new Columna("total", "Total", "dinero"), new Columna("dinero_devuelto", "Dinero devuelto", "dinero"),
                new Columna("motivo", "Motivo", "texto"), new Columna("anulada_por", "Autorizó", "texto"),
                new Columna("inventario_devuelto", "Inventario devuelto", "texto")),
                filas, totales(filas, "total", "dinero_devuelto"));
    }

    private Reporte cierresCaja(LocalDate d, LocalDate h, MapSqlParameterSource p) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT c.id AS caja, c.abierta_en, c.cerrada_en, ua.nombre AS abrio, uc.nombre AS cerro, c.monto_inicial,
                       coalesce((SELECT sum(m.monto) FROM movimientos_caja m WHERE m.caja_id = c.id AND m.tipo = 'VENTA' AND m.metodo = 'EFECTIVO'), 0) AS ventas_efectivo,
                       coalesce((SELECT sum(m.monto) FROM movimientos_caja m WHERE m.caja_id = c.id AND m.tipo = 'VENTA' AND m.metodo <> 'EFECTIVO'), 0) AS ventas_otros_medios,
                       c.efectivo_esperado, c.efectivo_contado, c.diferencia
                FROM cajas c JOIN usuarios ua ON ua.id = c.usuario_apertura_id LEFT JOIN usuarios uc ON uc.id = c.usuario_cierre_id
                WHERE c.estado = 'CERRADA' AND c.cerrada_en >= :desde AND c.cerrada_en < :hasta
                ORDER BY c.cerrada_en DESC""", p);
        return new Reporte("cierres-caja", TIPOS.get("cierres-caja"),
                "Cierres de caja del período. Efectivo esperado = saldo inicial + movimientos en efectivo; diferencia = contado − esperado.",
                d, h, List.of(new Columna("caja", "Caja", "numero"), new Columna("abierta_en", "Apertura", "fechahora"),
                new Columna("cerrada_en", "Cierre", "fechahora"), new Columna("abrio", "Abrió", "texto"),
                new Columna("cerro", "Cerró", "texto"), new Columna("monto_inicial", "Saldo inicial", "dinero"),
                new Columna("ventas_efectivo", "Ventas efectivo", "dinero"),
                new Columna("ventas_otros_medios", "Tarjeta y transferencia", "dinero"),
                new Columna("efectivo_esperado", "Esperado", "dinero"), new Columna("efectivo_contado", "Contado", "dinero"),
                new Columna("diferencia", "Diferencia", "dinero")),
                filas, totales(filas, "ventas_efectivo", "ventas_otros_medios", "diferencia"));
    }

    private static Map<String, Object> totales(List<Map<String, Object>> filas, String... claves) {
        Map<String, Object> t = new LinkedHashMap<>();
        for (String c : claves) {
            BigDecimal suma = BigDecimal.ZERO;
            for (Map<String, Object> f : filas) {
                Object v = f.get(c);
                if (v instanceof Number n) suma = suma.add(new BigDecimal(n.toString()));
            }
            t.put(c, suma);
        }
        return t;
    }

    /** Exporta el reporte a CSV (UTF-8 con BOM para que Excel muestre bien los acentos). */
    public String aCsv(Reporte r) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(csv(r.titulo())).append('\n');
        sb.append(csv("Del " + FECHA.format(r.desde()) + " al " + FECHA.format(r.hasta()))).append('\n');
        sb.append(String.join(",", r.columnas().stream().map(c -> csv(c.titulo())).toList())).append('\n');
        for (Map<String, Object> f : r.filas()) {
            sb.append(String.join(",", r.columnas().stream().map(c -> csv(valor(f.get(c.clave())))).toList())).append('\n');
        }
        if (r.totales() != null && !r.totales().isEmpty()) {
            List<String> tot = new ArrayList<>();
            for (int i = 0; i < r.columnas().size(); i++) {
                Columna c = r.columnas().get(i);
                tot.add(i == 0 ? csv("TOTAL") : csv(valor(r.totales().get(c.clave()))));
            }
            sb.append(String.join(",", tot)).append('\n');
        }
        return sb.toString();
    }

    private static final java.time.format.DateTimeFormatter FECHA_HORA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final java.time.format.DateTimeFormatter FECHA =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static String valor(Object v) {
        if (v == null) return "";
        if (v instanceof BigDecimal b) return b.setScale(Math.max(2, Math.min(b.scale(), 3)), java.math.RoundingMode.HALF_UP).toPlainString();
        if (v instanceof java.sql.Timestamp t) return FECHA_HORA.format(t.toInstant().atZone(Fechas.ZONA));
        if (v instanceof OffsetDateTime o) return FECHA_HORA.format(o.atZoneSameInstant(Fechas.ZONA));
        if (v instanceof java.sql.Date d) return FECHA.format(d.toLocalDate());
        if (v instanceof java.time.LocalDate d) return FECHA.format(d);
        String texto = v.toString();
        // Periodos agrupados por dia (aaaa-mm-dd) o por mes (aaaa-mm)
        if (texto.matches("\\d{4}-\\d{2}-\\d{2}")) return FECHA.format(java.time.LocalDate.parse(texto));
        if (texto.matches("\\d{4}-\\d{2}")) return texto.substring(5) + "/" + texto.substring(0, 4);
        return texto;
    }

    private static String csv(String s) {
        if (s == null) return "";
        // Evita que Excel interprete el contenido como formula
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
