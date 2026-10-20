package com.taqueria.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalogo de permisos del sistema. Cada cargo (rol) tiene una lista de estos codigos
 * en la tabla rol_permisos; el administrador los tiene todos. La API los valida con
 * {@code @PreAuthorize("hasAuthority('...')")}.
 */
public final class Permisos {

    public static final String DASHBOARD_VER = "dashboard.ver";
    public static final String REPORTES_VER = "reportes.ver";
    public static final String PRODUCTOS_VER = "productos.ver";
    public static final String PRODUCTOS_GESTIONAR = "productos.gestionar";
    public static final String RECETAS_GESTIONAR = "recetas.gestionar";
    public static final String INVENTARIO_VER = "inventario.ver";
    public static final String INVENTARIO_ENTRADAS = "inventario.entradas";
    public static final String INVENTARIO_SALIDAS = "inventario.salidas";
    public static final String INVENTARIO_AJUSTAR = "inventario.ajustar";
    public static final String INVENTARIO_GESTIONAR = "inventario.gestionar";
    public static final String PEDIDOS_VER = "pedidos.ver";
    public static final String PEDIDOS_CREAR = "pedidos.crear";
    public static final String PEDIDOS_ESTADO = "pedidos.estado";
    public static final String PEDIDOS_CANCELAR = "pedidos.cancelar";
    public static final String VENTAS_VER = "ventas.ver";
    public static final String VENTAS_COBRAR = "ventas.cobrar";
    public static final String VENTAS_ANULAR = "ventas.anular";
    public static final String COMPROBANTES_VER = "comprobantes.ver";
    public static final String COMPROBANTES_IMPRIMIR = "comprobantes.imprimir";
    public static final String CAJA_VER = "caja.ver";
    public static final String CAJA_OPERAR = "caja.operar";
    public static final String GASTOS_VER = "gastos.ver";
    public static final String GASTOS_GESTIONAR = "gastos.gestionar";
    public static final String PROVEEDORES_GESTIONAR = "proveedores.gestionar";
    public static final String COMPRAS_VER = "compras.ver";
    public static final String COMPRAS_GESTIONAR = "compras.gestionar";
    public static final String COMPRAS_RECIBIR = "compras.recibir";
    public static final String CLIENTES_VER = "clientes.ver";
    public static final String CLIENTES_GESTIONAR = "clientes.gestionar";
    public static final String USUARIOS_GESTIONAR = "usuarios.gestionar";
    public static final String CONFIGURACION_GESTIONAR = "configuracion.gestionar";
    public static final String AUDITORIA_VER = "auditoria.ver";

    /** codigo -> descripcion, en el orden en que se muestran. */
    public static final Map<String, String> CATALOGO = new LinkedHashMap<>();

    static {
        CATALOGO.put(DASHBOARD_VER, "Ver el panel principal");
        CATALOGO.put(REPORTES_VER, "Consultar y exportar reportes");
        CATALOGO.put(PRODUCTOS_VER, "Consultar el menú");
        CATALOGO.put(PRODUCTOS_GESTIONAR, "Crear y editar productos, precios, categorías y complementos");
        CATALOGO.put(RECETAS_GESTIONAR, "Configurar recetas");
        CATALOGO.put(INVENTARIO_VER, "Consultar existencias y movimientos");
        CATALOGO.put(INVENTARIO_ENTRADAS, "Registrar entradas de inventario");
        CATALOGO.put(INVENTARIO_SALIDAS, "Registrar salidas autorizadas de inventario");
        CATALOGO.put(INVENTARIO_AJUSTAR, "Registrar ajustes de inventario");
        CATALOGO.put(INVENTARIO_GESTIONAR, "Crear y editar artículos de inventario");
        CATALOGO.put(PEDIDOS_VER, "Consultar pedidos y panel de cocina");
        CATALOGO.put(PEDIDOS_CREAR, "Tomar pedidos");
        CATALOGO.put(PEDIDOS_ESTADO, "Cambiar el estado de los pedidos");
        CATALOGO.put(PEDIDOS_CANCELAR, "Cancelar pedidos no cobrados");
        CATALOGO.put(VENTAS_VER, "Consultar ventas");
        CATALOGO.put(VENTAS_COBRAR, "Registrar ventas y pagos");
        CATALOGO.put(VENTAS_ANULAR, "Anular ventas y autorizar devoluciones");
        CATALOGO.put(COMPROBANTES_VER, "Consultar comprobantes");
        CATALOGO.put(COMPROBANTES_IMPRIMIR, "Imprimir y reimprimir comprobantes");
        CATALOGO.put(CAJA_VER, "Consultar la caja y cierres anteriores");
        CATALOGO.put(CAJA_OPERAR, "Abrir y cerrar caja, registrar entradas y retiros");
        CATALOGO.put(GASTOS_VER, "Consultar gastos");
        CATALOGO.put(GASTOS_GESTIONAR, "Registrar y anular gastos");
        CATALOGO.put(PROVEEDORES_GESTIONAR, "Administrar proveedores");
        CATALOGO.put(COMPRAS_VER, "Consultar compras y deudas con proveedores");
        CATALOGO.put(COMPRAS_GESTIONAR, "Registrar compras y pagos a proveedores");
        CATALOGO.put(COMPRAS_RECIBIR, "Confirmar la recepción de compras");
        CATALOGO.put(CLIENTES_VER, "Consultar clientes");
        CATALOGO.put(CLIENTES_GESTIONAR, "Registrar y editar clientes");
        CATALOGO.put(USUARIOS_GESTIONAR, "Administrar empleados, usuarios y permisos");
        CATALOGO.put(CONFIGURACION_GESTIONAR, "Modificar la configuración del negocio");
        CATALOGO.put(AUDITORIA_VER, "Consultar el registro de auditoría");
    }

    private Permisos() {
    }
}
