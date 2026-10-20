// Formato del comprobante para papel termico de 58 u 80 mm y su version PDF.
import { esc, dinero, fechaHora, nombre } from './ui.js';

/** Convierte la vista del servidor (ComprobanteVista) en lineas de datos para dibujar. */
function datos(v) {
    const c = v.comprobante;
    const venta = v.venta;
    const cambio = venta.pagos.filter(p => p.estado === 'APLICADO').reduce((s, p) => s + Number(p.cambio || 0), 0);
    const recibido = venta.pagos.filter(p => p.estado === 'APLICADO' && p.metodo === 'EFECTIVO')
        .reduce((s, p) => s + Number(p.montoRecibido || p.monto), 0);
    return { c, venta, cambio, recibido };
}

/** HTML del ticket. copia: numero de impresion que se mostrara (0 = original). */
export function htmlTicket(v, { copia = 0, prueba = false } = {}) {
    const { c, venta, cambio, recibido } = datos(v);
    const fila = (izq, der, clase = '') => `<tr class="${clase}"><td>${izq}</td><td class="der">${der}</td></tr>`;
    return `
        ${v.logoUrl ? `<img class="logo-ticket" src="${esc(v.logoUrl)}" alt="">` : ''}
        <div class="centro negrita grande">${esc(c.negocioNombre)}</div>
        ${c.negocioDireccion ? `<div class="centro">${esc(c.negocioDireccion)}</div>` : ''}
        ${c.negocioTelefono ? `<div class="centro">Tel. ${esc(c.negocioTelefono)}</div>` : ''}
        ${c.negocioNit ? `<div class="centro">NIT: ${esc(c.negocioNit)}</div>` : ''}
        ${c.negocioNrc ? `<div class="centro">NRC: ${esc(c.negocioNrc)}</div>` : ''}
        <hr>
        <div class="centro negrita">${prueba ? 'PÁGINA DE PRUEBA DE IMPRESIÓN' : 'COMPROBANTE INTERNO DE VENTA'}</div>
        <div class="centro negrita">${esc(c.numero)}</div>
        ${c.estado === 'ANULADO' ? '<div class="anulado">*** ANULADO ***</div>' : ''}
        ${copia > 0 ? `<div class="centro">*** REIMPRESIÓN (copia ${copia}) ***</div>` : ''}
        <table>
            ${fila('Fecha:', fechaHora(c.fechaEmision))}
            ${fila('Venta:', esc(venta.numero))}
            ${venta.pedidoNumero ? fila('Pedido:', esc(venta.pedidoNumero) + ' · ' + esc(nombre(venta.tipoPedido))) : ''}
            ${fila('Atendió:', esc(v.emitidoPor || venta.usuario || ''))}
        </table>
        ${c.clienteNombre ? `<div>Cliente: ${esc(c.clienteNombre)}</div>` : '<div>Cliente: Consumidor final</div>'}
        ${c.clienteDocumento ? `<div>Doc.: ${esc(c.clienteDocumento)}</div>` : ''}
        ${c.clienteDireccion ? `<div>Dir.: ${esc(c.clienteDireccion)}</div>` : ''}
        <hr>
        <table>
            ${venta.detalles.map(d => `<tr><td colspan="2">${d.cantidad} x ${esc(d.descripcion)}</td></tr>
                <tr><td>&nbsp;&nbsp;${dinero(d.precioUnitario)} c/u</td><td class="der">${dinero(d.subtotal)}</td></tr>
                ${d.observaciones ? `<tr><td colspan="2">&nbsp;&nbsp;(${esc(d.observaciones)})</td></tr>` : ''}`).join('')}
        </table>
        <hr>
        <table>
            ${fila('Subtotal', dinero(venta.subtotal))}
            ${Number(venta.descuento) > 0 ? fila('Descuento', '-' + dinero(venta.descuento)) : ''}
            ${fila('Valor sin IVA', dinero(venta.baseImponible))}
            ${fila(`IVA incluido (${Number(v.ivaPorcentaje)}%)`, dinero(venta.impuesto))}
            ${fila('TOTAL', dinero(venta.total), 'negrita grande')}
        </table>
        <hr>
        <table>
            ${venta.pagos.filter(p => p.estado === 'APLICADO').map(p => fila(esc(nombre(p.metodo)) + (p.referencia ? ' ref. ' + esc(p.referencia) : ''), dinero(p.monto))).join('')}
            ${recibido > 0 ? fila('Efectivo recibido', dinero(recibido)) : ''}
            ${cambio > 0 ? fila('Cambio', dinero(cambio), 'negrita') : ''}
            ${venta.estado === 'PENDIENTE_PAGO' ? fila('PENDIENTE DE PAGO', dinero(venta.saldo), 'negrita') : ''}
        </table>
        ${c.mensaje ? `<hr><div class="centro">${esc(c.mensaje)}</div>` : ''}
        <div class="leyenda">${esc(v.leyenda)}</div>
        <div class="centro" style="margin-top:2mm">.</div>`;
}

/** Genera un PDF del ancho del papel con el mismo contenido (texto monoespaciado). */
export function pdfTicket(v, ancho) {
    const { jsPDF } = window.jspdf;
    const { c, venta, cambio, recibido } = datos(v);
    const columnas = ancho === 58 ? 32 : 42;
    const lineas = [];
    const centro = t => { String(t).match(new RegExp(`.{1,${columnas}}`, 'g'))?.forEach(x => lineas.push(' '.repeat(Math.max(0, Math.floor((columnas - x.length) / 2))) + x)); };
    const par = (izq, der) => {
        const espacio = columnas - der.length - 1;
        const i = izq.length > espacio ? izq.slice(0, espacio) : izq;
        lineas.push(i + ' '.repeat(Math.max(1, columnas - i.length - der.length)) + der);
    };
    const texto = t => String(t).match(new RegExp(`.{1,${columnas}}`, 'g'))?.forEach(x => lineas.push(x));
    const raya = () => lineas.push('-'.repeat(columnas));
    centro(c.negocioNombre);
    if (c.negocioDireccion) centro(c.negocioDireccion);
    if (c.negocioTelefono) centro('Tel. ' + c.negocioTelefono);
    if (c.negocioNit) centro('NIT: ' + c.negocioNit);
    if (c.negocioNrc) centro('NRC: ' + c.negocioNrc);
    raya();
    centro('COMPROBANTE INTERNO DE VENTA');
    centro(c.numero);
    if (c.estado === 'ANULADO') centro('*** ANULADO ***');
    par('Fecha:', fechaHora(c.fechaEmision));
    par('Venta:', venta.numero);
    if (venta.pedidoNumero) par('Pedido:', venta.pedidoNumero);
    par('Atendió:', v.emitidoPor || venta.usuario || '');
    texto('Cliente: ' + (c.clienteNombre || 'Consumidor final'));
    if (c.clienteDocumento) texto('Doc.: ' + c.clienteDocumento);
    raya();
    venta.detalles.forEach(d => {
        texto(`${d.cantidad} x ${d.descripcion}`);
        par(`  ${dinero(d.precioUnitario)} c/u`, dinero(d.subtotal));
        if (d.observaciones) texto(`  (${d.observaciones})`);
    });
    raya();
    par('Subtotal', dinero(venta.subtotal));
    if (Number(venta.descuento) > 0) par('Descuento', '-' + dinero(venta.descuento));
    par('Valor sin IVA', dinero(venta.baseImponible));
    par(`IVA incluido (${Number(v.ivaPorcentaje)}%)`, dinero(venta.impuesto));
    par('TOTAL', dinero(venta.total));
    raya();
    venta.pagos.filter(p => p.estado === 'APLICADO').forEach(p => par(nombre(p.metodo), dinero(p.monto)));
    if (recibido > 0) par('Efectivo recibido', dinero(recibido));
    if (cambio > 0) par('Cambio', dinero(cambio));
    if (venta.estado === 'PENDIENTE_PAGO') par('PENDIENTE DE PAGO', dinero(venta.saldo));
    if (c.mensaje) { raya(); centro(c.mensaje); }
    raya();
    centro(v.leyenda);

    const tamanoLetra = ancho === 58 ? 7 : 7.5;
    const alto = 8 + lineas.length * tamanoLetra * 0.3528 * 1.25;
    const doc = new jsPDF({ unit: 'mm', format: [ancho, Math.max(alto, 50)] });
    doc.setFont('courier', 'normal');
    doc.setFontSize(tamanoLetra);
    lineas.forEach((l, i) => doc.text(l, 2, 5 + i * tamanoLetra * 0.3528 * 1.25));
    doc.save(`comprobante-${c.numero}.pdf`);
}
