// Acciones sobre un pedido usadas en Pedidos y en el Panel de cocina.
import { api } from './api.js';
import { esc, dinero, fechaHora, nombre, estado, modal, aviso, pedirTexto } from './ui.js';
import { cobrarVenta, ventaRegistrada, registrarPago } from './cobro.js';

export const ICONO_ESTADO = { EN_PREPARACION: 'fire', LISTO: 'check2-circle', ENTREGADO: 'bag-check', PENDIENTE: 'arrow-counterclockwise', CANCELADO: 'x-circle' };
export const TEXTO_ACCION = { EN_PREPARACION: 'Preparar', LISTO: 'Marcar listo', ENTREGADO: 'Entregar', PENDIENTE: 'Regresar a pendiente', CANCELADO: 'Cancelar' };

/** Cambia el estado. Los retrocesos piden motivo; la cancelacion usa su propio endpoint. */
export async function cambiarEstado(pedido, nuevo) {
    if (nuevo === 'CANCELADO') return cancelarPedido(pedido);
    let motivo = null;
    const retroceso = (pedido.estado === 'EN_PREPARACION' && nuevo === 'PENDIENTE') || (pedido.estado === 'LISTO' && nuevo === 'EN_PREPARACION');
    if (retroceso) {
        const r = await pedirTexto(`El pedido ${pedido.numero} regresará a "${nombre(nuevo)}".`, { titulo: 'Regresar estado' });
        if (!r) return null;
        motivo = r.texto;
    }
    const p = await api(`/api/pedidos/${pedido.id}/estado`, { metodo: 'PUT', datos: { estado: nuevo, motivo } });
    aviso(`${p.numero}: ${nombre(p.estado)}`);
    return p;
}

export async function cancelarPedido(pedido) {
    if (pedido.venta && pedido.venta.estado !== 'ANULADA') {
        aviso('Este pedido ya tiene una venta registrada. Para cancelarlo, anule primero la venta ' + pedido.venta.numero + ' en Ventas.', 'alerta');
        return null;
    }
    const r = await pedirTexto(`Se cancelará el pedido ${pedido.numero}. Esta acción queda registrada.`, { titulo: 'Cancelar pedido', textoBoton: 'Cancelar pedido', peligro: true });
    if (!r) return null;
    const p = await api(`/api/pedidos/${pedido.id}/cancelar`, { datos: { motivo: r.texto } });
    aviso(`Pedido ${p.numero} cancelado`);
    return p;
}

export async function cobrarPedido(pedido) {
    const venta = await cobrarVenta({
        titulo: 'Cobrar pedido ' + pedido.numero, subtotal: Number(pedido.total),
        cliente: { id: pedido.clienteId, nombre: pedido.clienteNombre },
        enviar: (cobro, clave) => api(`/api/pedidos/${pedido.id}/venta`, { datos: cobro, clave })
    });
    if (venta) ventaRegistrada(venta);
    return venta;
}

export function lineasPedido(p) {
    return p.detalles.map(d => `<tr><td class="num">${d.cantidad}</td><td>${esc(d.nombreProducto)}
        ${d.complementos.length ? `<div class="small text-secondary">+ ${d.complementos.map(c => esc(c.nombre) + ' (' + dinero(c.precioExtra) + ')').join(', ')}</div>` : ''}
        ${d.observaciones ? `<div class="small text-danger"><i class="bi bi-chat-left-text"></i> ${esc(d.observaciones)}</div>` : ''}</td>
        <td class="num">${dinero(d.precioUnitario)}</td><td class="num">${dinero(d.subtotal)}</td></tr>`).join('');
}

/** Detalle del pedido con historial de estados y acciones segun permisos. */
export async function verPedido(id, { tiene, alCambiar }) {
    const [p, historial] = await Promise.all([api('/api/pedidos/' + id), api(`/api/pedidos/${id}/historial`)]);
    const lugar = p.tipo === 'LOCAL' ? (p.mesa ? 'Mesa: ' + esc(p.mesa) : 'Consumo en el local')
        : p.tipo === 'DOMICILIO' ? `Entregar en: ${esc(p.direccionEntrega)}${p.referenciaEntrega ? ' (' + esc(p.referenciaEntrega) + ')' : ''}` : 'Para llevar';
    const cuerpo = `<div class="d-flex flex-wrap gap-3 mb-2">
            <div>${estado(p.estado)}</div><div><i class="bi bi-clock"></i> ${fechaHora(p.creadoEn)}</div>
            <div><i class="bi bi-person"></i> Registró: ${esc(p.registradoPor || '')}</div></div>
        <p class="mb-1"><strong>${nombre(p.tipo)}</strong> · ${lugar}</p>
        ${p.clienteNombre ? `<p class="mb-1">Cliente: ${esc(p.clienteNombre)} ${p.clienteTelefono ? '· ' + esc(p.clienteTelefono) : ''}</p>` : ''}
        ${p.notas ? `<div class="alert alert-warning py-1 px-2 small mb-2"><i class="bi bi-sticky"></i> ${esc(p.notas)}</div>` : ''}
        <div class="tabla-contenedor"><table class="table table-sm"><thead><tr><th class="num">Cant.</th><th>Producto</th><th class="num">Precio</th><th class="num">Subtotal</th></tr></thead>
        <tbody>${lineasPedido(p)}</tbody><tfoot><tr><th colspan="3" class="text-end">Total</th><th class="num">${dinero(p.total)}</th></tr></tfoot></table></div>
        ${p.venta ? `<div class="border rounded p-2 mb-3 small">Venta <strong>${esc(p.venta.numero)}</strong> ${estado(p.venta.estado)}
            · Total ${dinero(p.venta.total)} ${Number(p.venta.saldo) > 0 && p.venta.estado !== 'ANULADA' ? '· Saldo ' + dinero(p.venta.saldo) : ''}
            ${p.venta.metodosPago ? '· ' + esc(p.venta.metodosPago) : ''}
            ${p.venta.comprobanteId ? ` · <a href="/comprobante.html?id=${p.venta.comprobanteId}">Comprobante ${esc(p.venta.comprobanteNumero)}</a>` : ''}</div>` : ''}
        <h3 class="h6">Historial de estados</h3>
        <ul class="list-unstyled small mb-0">${historial.map(h => `<li class="mb-1"><span class="text-secondary">${fechaHora(h.fecha)}</span> ·
            ${h.estadoAnterior ? esc(nombre(h.estadoAnterior)) + ' → ' : ''}<strong>${esc(nombre(h.estadoNuevo))}</strong> · ${esc(h.usuario || '')}
            ${h.motivo ? `<div class="text-secondary">Motivo: ${esc(h.motivo)}</div>` : ''}</li>`).join('')}</ul>`;

    const botones = [];
    const tras = async accion => { const r = await accion(); if (r !== null && r !== undefined) { md.cerrar(); alCambiar && alCambiar(); } };
    if (tiene('pedidos.estado')) {
        p.siguientesEstados.filter(e => e !== 'CANCELADO').forEach(e => botones.push({
            texto: TEXTO_ACCION[e] || nombre(e), clase: e === 'PENDIENTE' || e === 'EN_PREPARACION' && p.estado === 'LISTO' ? 'btn-outline-secondary' : 'btn-outline-primary',
            accion: () => tras(() => cambiarEstado(p, e))
        }));
    }
    if (tiene('pedidos.cancelar') && p.siguientesEstados.includes('CANCELADO') && !(p.venta && p.venta.estado !== 'ANULADA')) {
        botones.push({ texto: 'Cancelar pedido', clase: 'btn-outline-danger', accion: () => tras(() => cancelarPedido(p)) });
    }
    if (tiene('ventas.cobrar') && !p.venta && p.estado !== 'CANCELADO') {
        botones.push({ texto: 'Cobrar', clase: 'btn-primary', accion: async () => { md.cerrar(); const v = await cobrarPedido(p); if (v && alCambiar) alCambiar(); } });
    }
    if (tiene('ventas.cobrar') && p.venta && p.venta.estado === 'PENDIENTE_PAGO') {
        botones.push({ texto: 'Registrar pago', clase: 'btn-primary', accion: async () => {
            md.cerrar();
            const v = await api('/api/ventas/' + p.venta.id);
            if (await registrarPago(v) && alCambiar) alCambiar();
        } });
    }
    const md = modal({ titulo: 'Pedido ' + p.numero, cuerpo, botones, tamano: 'modal-lg' });
    return md;
}

