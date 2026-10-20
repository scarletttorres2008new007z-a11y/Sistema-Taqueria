// Ventas: busqueda, detalle con pagos, cobro de saldos pendientes y anulacion auditada.
import { api, consulta } from './comun/api.js';
import { esc, dinero, fechaHora, nombre, estado, vacio, paginacion, parametro, aviso, modal, opciones } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';
import { registrarPago } from './comun/cobro.js';

const { tiene } = await iniciarPagina({ titulo: 'Ventas', permiso: 'ventas.ver' });
const $ = id => document.getElementById(id);
const form = $('filtros');
let pagina = 0;

try {
    const nombres = await api('/api/usuarios/nombres');
    $('f-usuario').innerHTML = '<option value="">Todos</option>' +
        opciones(Object.entries(nombres).map(([id, n]) => ({ id, n })).sort((a, b) => a.n.localeCompare(b.n)), 'id', 'n');
} catch (e) { /* el filtro por cajero es opcional */ }

async function pendientes() {
    const lista = await api('/api/ventas/pendientes');
    if (!lista.length) { $('pendientes').innerHTML = ''; return; }
    const saldo = lista.reduce((s, v) => s + Number(v.saldo), 0);
    $('pendientes').innerHTML = `<section class="tarjeta"><div class="tarjeta-titulo"><i class="bi bi-hourglass-split"></i>
        Ventas pendientes de pago (${lista.length}) <span class="ms-auto">${dinero(saldo)}</span></div>
        <div class="tabla-contenedor"><table class="table table-sm mb-0"><tbody>${lista.map(v => `<tr>
            <td class="fw-semibold">${esc(v.numero)}</td><td>${fechaHora(v.creadoEn)}</td><td>${esc(v.clienteNombre || v.pedidoNumero || '')}</td>
            <td class="num">${dinero(v.total)}</td><td class="num text-danger">${dinero(v.saldo)}</td>
            <td class="text-end">${tiene('ventas.cobrar') ? `<button class="btn btn-sm btn-primary" data-pagar="${v.id}">Cobrar saldo</button>` : ''}
                <button class="btn btn-sm btn-outline-secondary" data-id="${v.id}">Ver</button></td></tr>`).join('')}</tbody></table></div></section>`;
}

async function cargar() {
    const cuerpo = $('filas');
    try {
        const f = { numero: form.numero.value.trim(), estado: form.estado.value, usuarioId: form.usuarioId.value,
            desde: form.desde.value, hasta: form.hasta.value, pagina, tamano: 20 };
        const r = await api('/api/ventas' + consulta(f));
        cuerpo.innerHTML = r.contenido.length ? r.contenido.map(v => `<tr>
            <td class="fw-semibold">${esc(v.numero)}</td><td class="text-nowrap">${fechaHora(v.creadoEn)}</td>
            <td>${esc(v.pedidoNumero || 'Directa')}</td><td>${esc(v.clienteNombre || '')}</td><td>${esc(v.usuario || '')}</td>
            <td class="num">${dinero(v.total)}</td><td class="num">${v.estado === 'PENDIENTE_PAGO' ? dinero(v.saldo) : ''}</td>
            <td>${estado(v.estado)}</td>
            <td class="text-end"><button class="btn btn-sm btn-outline-primary" data-id="${v.id}">Ver</button></td></tr>`).join('')
            : `<tr><td colspan="9">${vacio('No hay ventas con estos filtros', 'cash-coin')}</td></tr>`;
        paginacion($('pie'), r, p => { pagina = p; cargar(); });
        $('totales').textContent = !f.desde && !f.hasta && !f.numero ? 'Mostrando los últimos 7 días. Use las fechas para ver otro período.' : '';
    } catch (e) {
        cuerpo.innerHTML = `<tr><td colspan="9"><div class="alert alert-danger m-2">${esc(e.message)}</div></td></tr>`;
    }
}

function refrescar() {
    cargar();
    pendientes().catch(() => {});
}

async function verVenta(id) {
    const v = await api('/api/ventas/' + id);
    const pagos = v.pagos.length ? `<table class="table table-sm"><thead><tr><th>Fecha</th><th>Método</th><th class="num">Monto</th><th class="num">Recibido</th><th class="num">Cambio</th><th>Estado</th></tr></thead><tbody>
        ${v.pagos.map(p => `<tr><td>${fechaHora(p.fecha)}</td><td>${esc(nombre(p.metodo))}${p.referencia ? `<div class="small text-secondary">Ref. ${esc(p.referencia)}</div>` : ''}</td>
            <td class="num">${dinero(p.monto)}</td><td class="num">${p.montoRecibido !== null ? dinero(p.montoRecibido) : ''}</td>
            <td class="num">${p.cambio !== null ? dinero(p.cambio) : ''}</td><td>${estado(p.estado)}</td></tr>`).join('')}</tbody></table>`
        : '<p class="text-secondary small">Sin pagos registrados.</p>';
    const cuerpo = `<div class="d-flex flex-wrap gap-3 mb-2">${estado(v.estado)}<span><i class="bi bi-clock"></i> ${fechaHora(v.creadoEn)}</span>
            <span><i class="bi bi-person"></i> ${esc(v.usuario || '')}</span>${v.pedidoNumero ? `<span>Pedido <a href="/pedidos.html?id=${v.pedidoId}">${esc(v.pedidoNumero)}</a></span>` : '<span>Venta directa</span>'}</div>
        ${v.clienteNombre ? `<p class="mb-2">Cliente: ${esc(v.clienteNombre)} ${v.clienteDocumento ? '· ' + esc(v.clienteDocumento) : ''}</p>` : ''}
        ${v.estado === 'ANULADA' ? `<div class="alert alert-danger small">Anulada el ${fechaHora(v.anuladaEn)} por ${esc(v.anuladaPor || '')}. Motivo: ${esc(v.motivoAnulacion)}.
            ${v.inventarioDevuelto ? 'Se devolvió el inventario.' : 'No se devolvió el inventario.'}</div>` : ''}
        <table class="table table-sm"><thead><tr><th class="num">Cant.</th><th>Descripción</th><th class="num">Precio</th><th class="num">Subtotal</th></tr></thead><tbody>
        ${v.detalles.map(d => `<tr><td class="num">${d.cantidad}</td><td>${esc(d.descripcion)}${d.observaciones ? `<div class="small text-secondary">${esc(d.observaciones)}</div>` : ''}</td>
            <td class="num">${dinero(d.precioUnitario)}</td><td class="num">${dinero(d.subtotal)}</td></tr>`).join('')}</tbody>
        <tfoot class="small">
            <tr><td colspan="3" class="text-end">Subtotal</td><td class="num">${dinero(v.subtotal)}</td></tr>
            ${Number(v.descuento) > 0 ? `<tr><td colspan="3" class="text-end">Descuento</td><td class="num">-${dinero(v.descuento)}</td></tr>` : ''}
            <tr><td colspan="3" class="text-end">Valor sin IVA</td><td class="num">${dinero(v.baseImponible)}</td></tr>
            <tr><td colspan="3" class="text-end">IVA incluido (${Number(v.ivaPorcentaje)}%)</td><td class="num">${dinero(v.impuesto)}</td></tr>
            <tr class="fs-6"><th colspan="3" class="text-end">Total</th><th class="num">${dinero(v.total)}</th></tr>
            <tr><td colspan="3" class="text-end">Pagado</td><td class="num">${dinero(v.montoPagado)}</td></tr>
            ${v.estado === 'PENDIENTE_PAGO' ? `<tr><td colspan="3" class="text-end text-danger">Saldo pendiente</td><td class="num text-danger">${dinero(v.saldo)}</td></tr>` : ''}
            ${tiene('reportes.ver') ? `<tr><td colspan="3" class="text-end text-secondary">Costo estimado</td><td class="num text-secondary">${dinero(v.costoEstimado)}</td></tr>` : ''}
        </tfoot></table>
        <h3 class="h6">Pagos</h3>${pagos}`;
    const botones = [];
    if (v.comprobanteId) botones.push({ texto: 'Comprobante ' + v.comprobanteNumero, clase: 'btn-outline-primary', accion: async () => { location.href = '/comprobante.html?id=' + v.comprobanteId; } });
    if (tiene('ventas.anular') && v.estado !== 'ANULADA') botones.push({ texto: 'Anular venta', clase: 'btn-outline-danger', accion: async md => { md.cerrar(); anular(v); } });
    if (tiene('ventas.cobrar') && v.estado === 'PENDIENTE_PAGO') botones.push({ texto: 'Registrar pago', clase: 'btn-primary', accion: async md => { md.cerrar(); if (await registrarPago(v)) refrescar(); } });
    modal({ titulo: 'Venta ' + v.numero, cuerpo, botones, tamano: 'modal-lg' });
}

function anular(v) {
    const efectivo = v.pagos.filter(p => p.estado === 'APLICADO').reduce((s, p) => s + Number(p.monto), 0);
    modal({
        titulo: 'Anular venta ' + v.numero,
        cuerpo: `<div class="alert alert-warning small">La anulación no borra la venta: queda registrada con el motivo y el usuario.
            ${efectivo > 0 ? `Se registrará una <strong>devolución de ${dinero(efectivo)}</strong> en la caja abierta.` : ''}
            El comprobante ${esc(v.comprobanteNumero || '')} quedará como ANULADO y el pedido se cancelará.</div>
            <label class="form-label" for="a-motivo">Motivo de la anulación</label>
            <textarea class="form-control mb-3" id="a-motivo" rows="2" maxlength="250" required></textarea>
            <div class="form-check"><input class="form-check-input" type="checkbox" id="a-inv">
            <label class="form-check-label" for="a-inv">Devolver los ingredientes al inventario (solo si los productos no se prepararon ni se entregaron)</label></div>`,
        botones: [
            { texto: 'Volver', clase: 'btn-outline-secondary', cerrar: true },
            { texto: 'Anular venta', clase: 'btn-danger', accion: async md => {
                const motivo = md.el.querySelector('#a-motivo').value.trim();
                if (!motivo) throw new Error('Escriba el motivo de la anulación');
                await api(`/api/ventas/${v.id}/anular`, { datos: { motivo, devolverInventario: md.el.querySelector('#a-inv').checked } });
                aviso('Venta ' + v.numero + ' anulada');
                md.cerrar();
                refrescar();
            } }
        ]
    });
}

document.body.addEventListener('click', async ev => {
    const ver = ev.target.closest('[data-id]');
    const pagar = ev.target.closest('[data-pagar]');
    try {
        if (ver && ver.closest('#filas, #pendientes')) await verVenta(Number(ver.dataset.id));
        if (pagar) {
            const v = await api('/api/ventas/' + pagar.dataset.pagar);
            if (await registrarPago(v)) refrescar();
        }
    } catch (e) { aviso(e.message, 'error'); }
});
form.addEventListener('submit', ev => { ev.preventDefault(); pagina = 0; cargar(); });
form.addEventListener('reset', () => setTimeout(() => { pagina = 0; cargar(); }));

refrescar();
if (parametro('id')) verVenta(Number(parametro('id'))).catch(e => aviso(e.message, 'error'));
