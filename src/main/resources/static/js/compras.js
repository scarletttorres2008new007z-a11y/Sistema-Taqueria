// Compras a proveedores: registro, recepcion al inventario, pagos (de caja o no) y deudas.
import { api, consulta } from './comun/api.js';
import { esc, dinero, numero, fecha, fechaHora, nombre, estado, aviso, formulario, campo, opciones, vacio, modal, pedirTexto, confirmar, hoyISO, ocupado } from './comun/ui.js';
import { nuevaClave } from './comun/api.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Compras', permiso: 'compras.ver' });
const $ = id => document.getElementById(id);
const gestiona = tiene('compras.gestionar');
const recibe = tiene('compras.recibir');
if (!gestiona) $('nueva').classList.add('d-none');
let proveedores = [], items = [], compras = [], nombres = {};
const form = $('filtros');

async function inicio() {
    [proveedores, items, nombres] = await Promise.all([api('/api/proveedores'), api('/api/inventario'), api('/api/usuarios/nombres').catch(() => ({}))]);
    $('f-proveedor').innerHTML = opciones(proveedores, 'id', 'nombreComercial', null, 'Todos');
    cargar();
}

const pagoTxt = c => c.estado === 'ANULADA' ? '' : estado(c.estadoPago === 'PAGADA' ? 'PAGADA' : c.estadoPago === 'PARCIAL' ? 'PARCIAL' : 'PENDIENTE', c.estadoPago === 'PAGADA' ? 'Pagada' : c.estadoPago === 'PARCIAL' ? 'Pago parcial' : 'Pendiente de pago')
    + (c.vencida ? ' <span class="estado estado-VENCIDO">Vencida</span>' : '');

async function cargar() {
    compras = await api('/api/compras' + consulta({ desde: form.desde.value, hasta: form.hasta.value, proveedorId: form.proveedorId.value }));
    $('filas').innerHTML = compras.length ? compras.map(c => `<tr>
        <td class="fw-semibold">${esc(c.numero)}</td><td>${fecha(c.fecha)}</td><td>${esc(c.proveedor.nombreComercial)}</td><td>${esc(c.numeroFactura || '')}</td>
        <td class="num">${dinero(c.total)}</td><td class="num">${c.estado !== 'ANULADA' && Number(c.saldo) > 0 ? dinero(c.saldo) : ''}</td>
        <td>${estado(c.estado)}</td><td>${pagoTxt(c)}</td>
        <td class="text-end"><button class="btn btn-sm btn-outline-primary" data-id="${c.id}">Ver</button></td></tr>`).join('')
        : `<tr><td colspan="9">${vacio('No hay compras en este período', 'cart')}</td></tr>`;
}

function filaDetalle() {
    return `<tr><td><select class="form-select form-select-sm" data-c="item" aria-label="Artículo">${opciones(items.filter(i => i.activo), 'id', i => `${i.nombre} (${i.unidad.codigo})`, null, 'Seleccione')}</select></td>
        <td style="width:110px"><input class="form-control form-control-sm" type="number" min="0.001" step="0.001" data-c="cantidad" aria-label="Cantidad"></td>
        <td style="width:120px"><input class="form-control form-control-sm" type="number" min="0" step="0.0001" data-c="costo" aria-label="Costo unitario"></td>
        <td style="width:110px"><input class="form-control form-control-sm" data-c="lote" maxlength="60" aria-label="Lote"></td>
        <td style="width:150px"><input class="form-control form-control-sm" type="date" data-c="vence" aria-label="Vencimiento"></td>
        <td class="num" data-c="subtotal"></td>
        <td><button type="button" class="btn btn-sm btn-outline-danger" data-quitar aria-label="Quitar"><i class="bi bi-x-lg"></i></button></td></tr>`;
}

$('nueva').addEventListener('click', () => {
    if (!proveedores.some(p => p.activo)) { aviso('Primero registre un proveedor en Proveedores', 'alerta'); return; }
    const clave = nuevaClave();
    const m = modal({
        titulo: 'Nueva compra', tamano: 'modal-xl',
        cuerpo: `<form class="row g-2" novalidate id="c-form">
            ${campo('proveedorId', 'Proveedor', { col: 'col-md-4', extra: 'data-numero required', opcionesHtml: opciones(proveedores.filter(p => p.activo), 'id', 'nombreComercial', null, 'Seleccione') })}
            ${campo('fecha', 'Fecha de la compra', { tipo: 'date', col: 'col-md-2', extra: 'required' })}
            ${campo('numeroFactura', 'N.º de factura del proveedor', { col: 'col-md-3', extra: 'maxlength="60"' })}
            ${campo('fechaVencimientoPago', 'Pagar a más tardar', { tipo: 'date', col: 'col-md-3' })}
            <div class="col-12"><div class="tabla-contenedor"><table class="table table-sm align-middle mb-1">
                <thead><tr><th>Artículo</th><th>Cantidad</th><th>Costo unit.</th><th>Lote</th><th>Vence</th><th class="num">Subtotal</th><th></th></tr></thead>
                <tbody id="c-filas">${filaDetalle()}</tbody>
                <tfoot><tr><td colspan="5"><button type="button" class="btn btn-sm btn-outline-secondary" id="c-agregar"><i class="bi bi-plus"></i> Agregar artículo</button></td>
                    <th class="num" id="c-total">$0.00</th><td></td></tr></tfoot></table></div></div>
            ${campo('notas', 'Notas', { tipo: 'textarea', col: 'col-md-8', extra: 'maxlength="500"' })}
            ${recibe ? campo('recibirAhora', 'La mercadería ya llegó: ingresarla al inventario ahora', { tipo: 'checkbox', col: 'col-md-4' }) : ''}
        </form>`,
        botones: [
            { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
            { texto: 'Guardar compra', clase: 'btn-primary', accion: async md => {
                const f = md.el.querySelector('#c-form');
                const detalles = [...f.querySelectorAll('#c-filas tr')].map(tr => ({
                    itemId: Number(tr.querySelector('[data-c=item]').value) || null,
                    cantidad: Number(tr.querySelector('[data-c=cantidad]').value) || null,
                    costoUnitario: tr.querySelector('[data-c=costo]').value === '' ? null : Number(tr.querySelector('[data-c=costo]').value),
                    lote: tr.querySelector('[data-c=lote]').value.trim() || null,
                    fechaVencimiento: tr.querySelector('[data-c=vence]').value || null
                }));
                if (!f.proveedorId.value) throw new Error('Seleccione el proveedor');
                if (!detalles.length || detalles.some(d => !d.itemId || !d.cantidad || d.costoUnitario === null)) throw new Error('Complete artículo, cantidad y costo en cada fila');
                const datos = { proveedorId: Number(f.proveedorId.value), fecha: f.fecha.value, numeroFactura: f.numeroFactura.value.trim() || null,
                    fechaVencimientoPago: f.fechaVencimientoPago.value || null, notas: f.notas.value.trim() || null,
                    recibirAhora: f.recibirAhora ? f.recibirAhora.checked : false, detalles };
                const c = await api('/api/compras', { datos, clave });
                aviso(`Compra ${c.numero} registrada${c.estado === 'RECIBIDA' ? ' e ingresada al inventario' : ''}`);
                md.cerrar();
                items = await api('/api/inventario');
                cargar();
            } }
        ]
    });
    const f = m.el.querySelector('#c-form');
    f.fecha.value = hoyISO();
    const cuerpo = f.querySelector('#c-filas');
    const total = () => {
        let t = 0;
        cuerpo.querySelectorAll('tr').forEach(tr => {
            const s = Number(tr.querySelector('[data-c=cantidad]').value) * Number(tr.querySelector('[data-c=costo]').value);
            t += s;
            tr.querySelector('[data-c=subtotal]').textContent = s ? dinero(s) : '';
        });
        f.querySelector('#c-total').textContent = dinero(t);
    };
    cuerpo.addEventListener('input', total);
    cuerpo.addEventListener('change', ev => {
        if (ev.target.dataset.c === 'item') {
            const it = items.find(i => i.id === Number(ev.target.value));
            const costo = ev.target.closest('tr').querySelector('[data-c=costo]');
            if (it && costo.value === '') costo.value = Number(it.costoUnitario) || '';
            total();
        }
    });
    cuerpo.addEventListener('click', ev => { if (ev.target.closest('[data-quitar]') && cuerpo.children.length > 1) { ev.target.closest('tr').remove(); total(); } });
    f.querySelector('#c-agregar').addEventListener('click', () => cuerpo.insertAdjacentHTML('beforeend', filaDetalle()));
});

async function verCompra(id) {
    const c = await api('/api/compras/' + id);
    const cuerpo = `<div class="d-flex flex-wrap gap-3 mb-2">${estado(c.estado)} ${pagoTxt(c)}<span>Fecha ${fecha(c.fecha)}</span>
            ${c.numeroFactura ? `<span>Factura ${esc(c.numeroFactura)}</span>` : ''}${c.fechaVencimientoPago ? `<span>Pagar antes del ${fecha(c.fechaVencimientoPago)}</span>` : ''}</div>
        <p class="mb-2"><strong>${esc(c.proveedor.nombreComercial)}</strong> ${c.proveedor.telefono ? '· ' + esc(c.proveedor.telefono) : ''}</p>
        ${c.fechaRecepcion ? `<p class="small text-secondary">Recibida el ${fechaHora(c.fechaRecepcion)} por ${esc(nombres[c.recibidoPor] || '')}</p>` : ''}
        <table class="table table-sm"><thead><tr><th>Artículo</th><th class="num">Cantidad</th><th class="num">Costo unit.</th><th class="num">Subtotal</th><th>Lote / vence</th></tr></thead><tbody>
        ${c.detalles.map(d => `<tr><td>${esc(d.item.nombre)}</td><td class="num">${numero(d.cantidad)} ${esc(d.item.unidad.codigo)}</td><td class="num">${dinero(d.costoUnitario)}</td>
            <td class="num">${dinero(d.subtotal)}</td><td class="small">${esc(d.lote || '')} ${d.fechaVencimiento ? fecha(d.fechaVencimiento) : ''}</td></tr>`).join('')}</tbody>
        <tfoot><tr><th colspan="3" class="text-end">Total</th><th class="num">${dinero(c.total)}</th><td></td></tr>
            <tr><td colspan="3" class="text-end">Pagado</td><td class="num">${dinero(c.montoPagado)}</td><td></td></tr>
            <tr><td colspan="3" class="text-end">Saldo</td><td class="num">${dinero(c.saldo)}</td><td></td></tr></tfoot></table>
        <h3 class="h6">Pagos</h3>
        ${c.pagos.length ? `<table class="table table-sm"><tbody>${c.pagos.map(p => `<tr><td>${fecha(p.fecha)}</td><td>${esc(nombre(p.metodo))}${p.desdeCaja ? ' (de caja)' : ''}</td>
            <td>${esc(p.nota || '')}</td><td>${esc(nombres[p.usuarioId] || '')}</td><td class="num">${dinero(p.monto)}</td></tr>`).join('')}</tbody></table>` : '<p class="small text-secondary">Sin pagos.</p>'}
        ${c.notas ? `<p class="small">Notas: ${esc(c.notas)}</p>` : ''}`;
    const botones = [];
    if (gestiona && c.estado === 'PENDIENTE_RECEPCION' && Number(c.montoPagado) === 0) botones.push({ texto: 'Anular', clase: 'btn-outline-danger me-auto', accion: async md => {
        const r = await pedirTexto('La compra quedará anulada y no ingresará al inventario.', { titulo: 'Anular compra', peligro: true, textoBoton: 'Anular' });
        if (!r) return;
        await api(`/api/compras/${c.id}/anular`, { datos: { motivo: r.texto } }); aviso('Compra anulada'); md.cerrar(); cargar();
    } });
    if (gestiona && c.estado !== 'ANULADA' && Number(c.saldo) > 0) botones.push({ texto: 'Registrar pago', clase: 'btn-outline-primary', accion: async md => { md.cerrar(); pagar(c); } });
    if (recibe && c.estado === 'PENDIENTE_RECEPCION') botones.push({ texto: 'Recibir mercadería', clase: 'btn-primary', accion: async md => {
        if (!await confirmar(`Se sumarán ${c.detalles.length} artículo(s) al inventario. Esta operación se hace una sola vez.`, { titulo: 'Recibir compra ' + c.numero, textoBoton: 'Recibir' })) return;
        await api(`/api/compras/${c.id}/recibir`, { metodo: 'POST', clave: 'recibir-compra-' + c.id + '-' + c.version });
        aviso('Mercadería ingresada al inventario'); md.cerrar(); cargar();
    } });
    modal({ titulo: 'Compra ' + c.numero, cuerpo, botones, tamano: 'modal-lg' });
}

function pagar(c) {
    formulario({
        titulo: 'Pago de la compra ' + c.numero, textoBoton: 'Registrar pago',
        html: `<p>Saldo pendiente: <strong>${dinero(c.saldo)}</strong></p><div class="row g-2">
            ${campo('monto', 'Monto', { tipo: 'number', col: 'col-6', extra: `min="0.01" max="${c.saldo}" step="0.01" required` })}
            ${campo('fecha', 'Fecha', { tipo: 'date', col: 'col-6' })}
            ${campo('metodo', 'Forma de pago', { col: 'col-6', opcionesHtml: '<option value="EFECTIVO">Efectivo</option><option value="TRANSFERENCIA">Transferencia</option><option value="TARJETA">Tarjeta</option>' })}
            ${campo('desdeCaja', 'Pagar con efectivo de la caja abierta', { tipo: 'checkbox', col: 'col-6' })}
            ${campo('nota', 'Nota', { extra: 'maxlength="250"' })}</div>`,
        valores: { monto: c.saldo, fecha: hoyISO(), metodo: 'EFECTIVO' },
        alAbrir: f => { const v = () => { if (f.metodo.value !== 'EFECTIVO') f.desdeCaja.checked = false; f.desdeCaja.disabled = f.metodo.value !== 'EFECTIVO'; }; f.metodo.addEventListener('change', v); v(); },
        guardar: async (datos, f, clave) => {
            await api(`/api/compras/${c.id}/pagos`, { datos, clave });
            aviso('Pago registrado');
            cargar();
            if ($('tab-deudas').classList.contains('active')) deudas();
        }
    });
}

async function deudas() {
    const lista = await api('/api/compras/deudas');
    $('deudas').innerHTML = lista.length ? lista.map(d => `<section class="tarjeta mb-3"><div class="tarjeta-titulo">${esc(d.proveedor)} ${d.telefono ? `<span class="small text-secondary">${esc(d.telefono)}</span>` : ''}
        <span class="ms-auto">Saldo ${dinero(d.saldo)}</span>${d.vencidas ? `<span class="estado estado-VENCIDO">${d.vencidas} vencida(s)</span>` : ''}</div>
        <div class="tabla-contenedor"><table class="table table-sm mb-0"><tbody>${d.compras.map(c => `<tr><td>${esc(c.numero)}</td><td>${esc(c.numeroFactura || '')}</td><td>${fecha(c.fecha)}</td>
            <td>${c.fechaVencimientoPago ? 'Vence ' + fecha(c.fechaVencimientoPago) : ''} ${c.vencida ? '<span class="estado estado-VENCIDO">Vencida</span>' : ''}</td>
            <td class="num">${dinero(c.total)}</td><td class="num fw-semibold">${dinero(c.saldo)}</td>
            <td class="text-end"><button class="btn btn-sm btn-outline-primary" data-id="${c.id}">Ver</button></td></tr>`).join('')}</tbody></table></div></section>`).join('')
        : `<div class="tarjeta">${vacio('No hay deudas pendientes con proveedores', 'check2-circle')}</div>`;
}
$('pestana-deudas').addEventListener('shown.bs.tab', () => deudas().catch(e => aviso(e.message, 'error')));

document.querySelector('.tab-content').addEventListener('click', ev => {
    const b = ev.target.closest('[data-id]');
    if (b) verCompra(Number(b.dataset.id)).catch(e => aviso(e.message, 'error'));
});
form.addEventListener('submit', ev => { ev.preventDefault(); cargar().catch(e => aviso(e.message, 'error')); });
inicio().catch(e => aviso(e.message, 'error'));
