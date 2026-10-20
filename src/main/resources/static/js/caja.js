// Caja: apertura, movimientos de efectivo, cierre con conteo y diferencia, e historial.
import { api } from './comun/api.js';
import { esc, dinero, fechaHora, nombre, aviso, formulario, campo, modal, vacio, paginacion } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Caja', permiso: 'caja.ver' });
const $ = id => document.getElementById(id);
const opera = tiene('caja.operar');
let paginaCierres = 0;
let resumenActual = null;

function tablaResumen(r) {
    const fila = (t, v, clase = '') => `<tr class="${clase}"><td>${t}</td><td class="num">${v}</td></tr>`;
    return `<table class="table table-sm mb-0">
        ${fila('Fondo inicial', dinero(r.caja.montoInicial))}
        ${fila('+ Ventas en efectivo', dinero(r.ventasEfectivo))}
        ${fila('+ Entradas de efectivo', dinero(r.ingresos))}
        ${fila('− Devoluciones en efectivo', dinero(r.devoluciones))}
        ${fila('− Retiros', dinero(r.retiros))}
        ${fila('− Gastos pagados con caja', dinero(r.gastos))}
        ${fila('− Pagos a proveedores con caja', dinero(r.pagosProveedores))}
        ${fila('<strong>Efectivo esperado en caja</strong>', `<strong>${dinero(r.efectivoEsperado)}</strong>`, 'table-light')}
        ${fila('Ventas con tarjeta (no entran a la gaveta)', dinero(r.ventasTarjeta), 'text-secondary')}
        ${fila('Ventas por transferencia (no entran a la gaveta)', dinero(r.ventasTransferencia), 'text-secondary')}
        ${r.caja.estado === 'CERRADA' ? fila('Efectivo contado', dinero(r.caja.efectivoContado)) +
            fila('Diferencia (contado − esperado)', dinero(r.caja.diferencia), Number(r.caja.diferencia) < 0 ? 'text-danger fw-semibold' : Number(r.caja.diferencia) > 0 ? 'text-success fw-semibold' : '') : ''}
    </table>`;
}

function tablaMovimientos(r) {
    return r.movimientos.length ? `<div class="tabla-contenedor"><table class="table table-sm mb-0">
        <thead><tr><th>Hora</th><th>Tipo</th><th>Método</th><th>Concepto</th><th>Usuario</th><th class="num">Monto</th></tr></thead><tbody>
        ${r.movimientos.map(m => `<tr><td class="text-nowrap">${fechaHora(m.fecha)}</td><td>${esc(nombre(m.tipo))}</td><td>${esc(nombre(m.metodo))}</td>
            <td>${esc(m.concepto)}</td><td>${esc(m.usuario || '')}</td>
            <td class="num ${Number(m.monto) < 0 ? 'text-danger' : ''}">${dinero(m.monto)}</td></tr>`).join('')}</tbody></table></div>`
        : vacio('Todavía no hay movimientos en esta caja');
}

async function cargarActual() {
    const r = await api('/api/caja/actual');
    if (!r.abierta) {
        resumenActual = null;
        $('actual').innerHTML = `<section class="tarjeta"><div class="tarjeta-cuerpo text-center py-4">
            <i class="bi bi-safe fs-1 text-secondary"></i><h2 class="h5 mt-2">La caja está cerrada</h2>
            <p class="text-secondary">Para cobrar en efectivo, tarjeta o transferencia hay que abrir la caja con el fondo inicial.</p>
            ${opera ? '<button class="btn btn-primary" id="abrir" type="button"><i class="bi bi-unlock"></i> Abrir caja</button>' : ''}</div></section>`;
        if (opera) $('abrir').addEventListener('click', abrir);
        return;
    }
    const s = resumenActual = r.resumen;
    $('actual').innerHTML = `<div class="row g-3">
        <div class="col-lg-5"><section class="tarjeta h-100"><div class="tarjeta-titulo"><i class="bi bi-unlock"></i> Caja abierta
            <span class="small text-secondary ms-auto">desde ${fechaHora(s.caja.abiertaEn)} · ${esc(s.abiertaPor || '')}</span></div>
            <div class="tarjeta-cuerpo">${tablaResumen(s)}
            ${opera ? `<div class="d-flex flex-wrap gap-2 mt-3">
                <button class="btn btn-sm btn-outline-success" id="ingreso" type="button"><i class="bi bi-plus-circle"></i> Entrada de efectivo</button>
                <button class="btn btn-sm btn-outline-danger" id="retiro" type="button"><i class="bi bi-dash-circle"></i> Retiro</button>
                <button class="btn btn-sm btn-primary ms-auto" id="cerrar" type="button"><i class="bi bi-lock"></i> Cerrar caja</button></div>` : ''}</div></section></div>
        <div class="col-lg-7"><section class="tarjeta h-100"><div class="tarjeta-titulo"><i class="bi bi-list-ul"></i> Movimientos de esta caja (${s.movimientos.length})</div>
            ${tablaMovimientos(s)}</section></div></div>`;
    if (opera) {
        $('ingreso').addEventListener('click', () => movimiento('INGRESO'));
        $('retiro').addEventListener('click', () => movimiento('RETIRO'));
        $('cerrar').addEventListener('click', cerrar);
    }
}

function abrir() {
    formulario({
        titulo: 'Abrir caja', textoBoton: 'Abrir caja',
        html: `<div class="row g-2">${campo('montoInicial', 'Fondo inicial en efectivo ($)', { tipo: 'number', extra: 'min="0" step="0.01" required', ayuda: 'Dinero con el que empieza la gaveta (para dar cambio).' })}
            ${campo('notas', 'Notas (opcional)', { tipo: 'textarea', extra: 'maxlength="250"' })}</div>`,
        valores: { montoInicial: '' },
        guardar: async (datos, f, clave) => {
            await api('/api/caja/abrir', { datos, clave });
            aviso('Caja abierta');
            cargarActual();
        }
    });
}

function movimiento(tipo) {
    formulario({
        titulo: tipo === 'INGRESO' ? 'Entrada de efectivo' : 'Retiro de efectivo', textoBoton: 'Registrar',
        html: `<div class="row g-2">${campo('monto', 'Monto ($)', { tipo: 'number', extra: 'min="0.01" step="0.01" required' })}
            ${campo('concepto', 'Concepto', { extra: 'maxlength="200" required', ayuda: tipo === 'INGRESO' ? 'Ej.: cambio adicional traído por el dueño' : 'Ej.: retiro parcial para depósito bancario' })}
            ${tipo === 'RETIRO' ? '<div class="col-12 small text-secondary">Los gastos del negocio se registran en Gastos (marcando "pagado desde caja"), no como retiro.</div>' : ''}</div>`,
        guardar: async (datos, f, clave) => {
            await api('/api/caja/movimientos', { datos: { ...datos, tipo }, clave });
            aviso('Movimiento registrado');
            cargarActual();
        }
    });
}

function cerrar() {
    const esperado = Number(resumenActual.efectivoEsperado);
    formulario({
        titulo: 'Cerrar caja', textoBoton: 'Cerrar caja',
        html: `<p>Cuente el efectivo de la gaveta y escriba el total. El sistema calculará la diferencia.</p>
            <div class="d-flex justify-content-between mb-2"><span>Efectivo esperado</span><strong>${dinero(esperado)}</strong></div>
            <div class="row g-2">${campo('efectivoContado', 'Efectivo contado ($)', { tipo: 'number', extra: 'min="0" step="0.01" required' })}
            <div class="col-12" id="diferencia"></div>
            ${campo('notas', 'Notas del cierre', { tipo: 'textarea', extra: 'maxlength="250"', ayuda: 'Explique cualquier diferencia.' })}</div>`,
        alAbrir: form => form.efectivoContado.addEventListener('input', () => {
            const v = form.efectivoContado.value;
            const d = Math.round((Number(v) - esperado) * 100) / 100;
            form.querySelector('#diferencia').innerHTML = v === '' ? '' : `<div class="alert py-1 px-2 mb-0 ${d === 0 ? 'alert-success' : d < 0 ? 'alert-danger' : 'alert-warning'}">
                ${d === 0 ? 'Cuadra exacto' : d < 0 ? 'Faltante de ' + dinero(-d) : 'Sobrante de ' + dinero(d)}</div>`;
        }),
        guardar: async (datos, f, clave) => {
            const r = await api('/api/caja/cerrar', { datos, clave });
            aviso('Caja cerrada. Diferencia: ' + dinero(r.caja.diferencia));
            cargarActual();
            cargarCierres();
            verCierre(r);
        }
    });
}

function verCierre(r) {
    modal({ titulo: 'Cierre de caja', tamano: 'modal-xl',
        cuerpo: `<div class="row g-3"><div class="col-lg-5">
            <p class="small mb-2">Abrió ${esc(r.abiertaPor || '')} el ${fechaHora(r.caja.abiertaEn)}<br>Cerró ${esc(r.cerradaPor || '')} el ${fechaHora(r.caja.cerradaEn)}</p>
            ${tablaResumen(r)}${r.caja.notas ? `<p class="small mt-2">Notas: ${esc(r.caja.notas)}</p>` : ''}</div>
            <div class="col-lg-7">${tablaMovimientos(r)}</div></div>`,
        botones: [{ texto: 'Imprimir', clase: 'btn-outline-primary', accion: async () => window.print() }, { texto: 'Cerrar', clase: 'btn-secondary', cerrar: true }] });
}

async function cargarCierres() {
    const r = await api('/api/caja/cierres?pagina=' + paginaCierres);
    $('filas-cierres').innerHTML = r.contenido.length ? r.contenido.map(c => `<tr>
        <td>${fechaHora(c.caja.abiertaEn)}</td><td>${fechaHora(c.caja.cerradaEn)}</td><td>${esc(c.abiertaPor || '')}</td><td>${esc(c.cerradaPor || '')}</td>
        <td class="num">${dinero(c.caja.montoInicial)}</td><td class="num">${dinero(c.caja.efectivoEsperado)}</td><td class="num">${dinero(c.caja.efectivoContado)}</td>
        <td class="num ${Number(c.caja.diferencia) < 0 ? 'text-danger' : ''}">${dinero(c.caja.diferencia)}</td>
        <td class="text-end"><button class="btn btn-sm btn-outline-primary" data-id="${c.caja.id}">Ver</button></td></tr>`).join('')
        : `<tr><td colspan="9">${vacio('Todavía no hay cierres de caja')}</td></tr>`;
    paginacion($('pie-cierres'), r, p => { paginaCierres = p; cargarCierres(); });
}
$('filas-cierres').addEventListener('click', async ev => {
    const b = ev.target.closest('[data-id]');
    if (b) try { verCierre(await api('/api/caja/' + b.dataset.id)); } catch (e) { aviso(e.message, 'error'); }
});

cargarActual().catch(e => aviso(e.message, 'error'));
cargarCierres().catch(e => aviso(e.message, 'error'));
