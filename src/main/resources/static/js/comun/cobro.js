// Ventana de cobro compartida: venta nueva (desde pedido o directa) y pagos de ventas pendientes.
// La clave de idempotencia se crea al abrir la ventana: si el cajero pulsa dos veces o
// se repite la solicitud, el servidor devuelve la misma venta en lugar de crear otra.

import { api, nuevaClave } from './api.js';
import { esc, dinero, modal, aviso } from './ui.js';

const METODOS = [['EFECTIVO', 'Efectivo'], ['TARJETA', 'Tarjeta'], ['TRANSFERENCIA', 'Transferencia']];
const r2 = n => Math.round((Number(n) || 0) * 100) / 100;

async function estadoCaja() {
    try {
        const c = await api('/api/caja/actual');
        return c.abierta;
    } catch (e) {
        return null;
    }
}

function filaPago(monto) {
    const div = document.createElement('div');
    div.className = 'border rounded p-2 mb-2 fila-pago';
    div.innerHTML = `<div class="row g-2 align-items-end">
        <div class="col-sm-4"><label class="form-label small mb-1">Método</label>
            <select class="form-select form-select-sm" data-c="metodo">${METODOS.map(([v, t]) => `<option value="${v}">${t}</option>`).join('')}</select></div>
        <div class="col-sm-3"><label class="form-label small mb-1">Monto</label>
            <input class="form-control form-control-sm" data-c="monto" type="number" min="0.01" step="0.01" value="${monto.toFixed(2)}"></div>
        <div class="col-sm-3 efectivo"><label class="form-label small mb-1">Efectivo recibido</label>
            <input class="form-control form-control-sm" data-c="recibido" type="number" min="0" step="0.01" placeholder="${monto.toFixed(2)}"></div>
        <div class="col-sm-3 d-none otro"><label class="form-label small mb-1">Referencia</label>
            <input class="form-control form-control-sm" data-c="referencia" maxlength="60" placeholder="N.º de autorización"></div>
        <div class="col-sm-2 text-end"><button type="button" class="btn btn-sm btn-outline-danger" data-quitar aria-label="Quitar pago"><i class="bi bi-x-lg"></i></button></div>
    </div>
    <div class="small mt-1 efectivo"><span class="text-secondary">Cambio:</span> <strong data-c="cambio">$0.00</strong></div>
    <div class="small text-secondary mt-1 d-none otro">Anote solo el número de autorización o los últimos 4 dígitos. No escriba el número completo de la tarjeta.</div>`;
    return div;
}

function leerPagos(cont) {
    return [...cont.querySelectorAll('.fila-pago')].map(f => {
        const metodo = f.querySelector('[data-c=metodo]').value;
        const monto = r2(f.querySelector('[data-c=monto]').value);
        const recibidoTxt = f.querySelector('[data-c=recibido]').value;
        return {
            metodo, monto,
            montoRecibido: metodo === 'EFECTIVO' && recibidoTxt !== '' ? r2(recibidoTxt) : null,
            referencia: metodo !== 'EFECTIVO' ? (f.querySelector('[data-c=referencia]').value.trim() || null) : null
        };
    });
}

function enlazarPagos(cont, totalFn, alCambiar) {
    const actualizar = () => {
        cont.querySelectorAll('.fila-pago').forEach(f => {
            const metodo = f.querySelector('[data-c=metodo]').value;
            f.querySelectorAll('.efectivo').forEach(e => e.classList.toggle('d-none', metodo !== 'EFECTIVO'));
            f.querySelectorAll('.otro').forEach(e => e.classList.toggle('d-none', metodo === 'EFECTIVO'));
            const monto = r2(f.querySelector('[data-c=monto]').value);
            const rec = f.querySelector('[data-c=recibido]');
            rec.placeholder = monto.toFixed(2);
            const recibido = rec.value === '' ? monto : r2(rec.value);
            const cambio = r2(recibido - monto);
            const el = f.querySelector('[data-c=cambio]');
            el.textContent = cambio < 0 ? 'Falta ' + dinero(-cambio) : dinero(cambio);
            el.className = cambio < 0 ? 'text-danger' : '';
        });
        alCambiar();
    };
    cont.addEventListener('input', actualizar);
    cont.addEventListener('change', actualizar);
    cont.addEventListener('click', ev => {
        const b = ev.target.closest('[data-quitar]');
        if (b) { b.closest('.fila-pago').remove(); actualizar(); }
    });
    return actualizar;
}

function validarPagos(pagos, total) {
    for (const p of pagos) {
        if (!(p.monto > 0)) throw new Error('Cada pago debe tener un monto mayor que cero');
        if (p.metodo === 'EFECTIVO' && p.montoRecibido !== null && p.montoRecibido < p.monto) {
            throw new Error('El efectivo recibido es menor que el monto del pago');
        }
    }
    const suma = r2(pagos.reduce((s, p) => s + p.monto, 0));
    if (suma > r2(total)) throw new Error(`Los pagos (${dinero(suma)}) superan el total (${dinero(total)})`);
}

/**
 * Cobro de una venta nueva.
 * @param {object} op { titulo, subtotal, cliente: {nombre}, enviar(cobro, clave) -> Promise<VentaVista> }
 * @returns {Promise<VentaVista|null>}
 */
export async function cobrarVenta({ titulo = 'Cobrar', subtotal, cliente = {}, enviar }) {
    const clave = nuevaClave();
    const cajaAbierta = await estadoCaja();
    return new Promise(resolve => {
        let resultado = null;
        const cuerpo = `
            ${cajaAbierta === false ? `<div class="alert alert-warning small"><i class="bi bi-exclamation-triangle"></i>
                La caja está cerrada. Para recibir pagos, primero abra la caja en <a href="/caja.html">Caja</a>.
                Puede registrar la venta como <strong>pendiente de pago</strong>.</div>` : ''}
            <div class="d-flex justify-content-between align-items-baseline mb-2">
                <span class="text-secondary">Subtotal</span><span class="fs-5">${dinero(subtotal)}</span></div>
            <details class="mb-2"><summary class="small">Aplicar descuento</summary>
                <div class="row g-2 mt-1">
                    <div class="col-4"><input class="form-control form-control-sm" id="c-descuento" type="number" min="0" step="0.01" placeholder="0.00" aria-label="Descuento en dólares"></div>
                    <div class="col-8"><input class="form-control form-control-sm" id="c-motivo" maxlength="200" placeholder="Motivo del descuento (obligatorio)" aria-label="Motivo del descuento"></div>
                </div></details>
            <div class="d-flex justify-content-between align-items-baseline border-top pt-2 mb-3">
                <strong>Total a cobrar</strong><strong class="fs-3" id="c-total">${dinero(subtotal)}</strong></div>
            <div class="form-check form-switch mb-2">
                <input class="form-check-input" type="checkbox" id="c-pendiente" ${cajaAbierta === false ? 'checked' : ''}>
                <label class="form-check-label" for="c-pendiente">Registrar la venta sin pago (queda pendiente de pago)</label>
            </div>
            <div id="c-zona-pagos">
                <div id="c-pagos"></div>
                <div class="d-flex justify-content-between align-items-center">
                    <button type="button" class="btn btn-sm btn-outline-secondary" id="c-agregar"><i class="bi bi-plus"></i> Dividir en otro método</button>
                    <span class="small" id="c-restante"></span>
                </div>
            </div>
            <details class="mt-3"><summary class="small">Datos del cliente para el comprobante (opcional)</summary>
                <div class="row g-2 mt-1">
                    <div class="col-12"><input class="form-control form-control-sm" id="c-cnombre" maxlength="150" placeholder="Nombre" value="${esc(cliente.nombre || '')}" aria-label="Nombre del cliente"></div>
                    <div class="col-6"><input class="form-control form-control-sm" id="c-cdoc" maxlength="30" placeholder="DUI o NIT" aria-label="Documento del cliente"></div>
                    <div class="col-6"><input class="form-control form-control-sm" id="c-cdir" maxlength="250" placeholder="Dirección" aria-label="Dirección del cliente"></div>
                </div></details>`;
        const m = modal({
            titulo, cuerpo, tamano: 'modal-lg',
            botones: [
                { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
                { texto: 'Confirmar venta', clase: 'btn-primary', accion: async md => {
                    const descuento = r2(md.el.querySelector('#c-descuento').value);
                    const motivo = md.el.querySelector('#c-motivo').value.trim();
                    if (descuento < 0 || descuento >= subtotal) throw new Error('El descuento debe ser menor que el subtotal');
                    if (descuento > 0 && !motivo) throw new Error('Escriba el motivo del descuento');
                    const total = r2(subtotal - descuento);
                    const pendiente = md.el.querySelector('#c-pendiente').checked;
                    const pagos = pendiente ? [] : leerPagos(md.el.querySelector('#c-pagos'));
                    if (!pendiente && !pagos.length) throw new Error('Agregue un pago o marque la venta como pendiente de pago');
                    validarPagos(pagos, total);
                    const cobro = {
                        descuento: descuento > 0 ? descuento : null,
                        motivoDescuento: descuento > 0 ? motivo : null,
                        clienteId: cliente.id || null,
                        clienteNombre: md.el.querySelector('#c-cnombre').value.trim() || null,
                        clienteDocumento: md.el.querySelector('#c-cdoc').value.trim() || null,
                        clienteDireccion: md.el.querySelector('#c-cdir').value.trim() || null,
                        pagos
                    };
                    resultado = await enviar(cobro, clave);
                    md.cerrar();
                } }
            ]
        });
        const el = m.el;
        const cont = el.querySelector('#c-pagos');
        const totalActual = () => r2(subtotal - r2(el.querySelector('#c-descuento').value));
        const refrescar = () => {
            const total = totalActual();
            el.querySelector('#c-total').textContent = dinero(total);
            const pendiente = el.querySelector('#c-pendiente').checked;
            el.querySelector('#c-zona-pagos').classList.toggle('d-none', pendiente);
            const suma = r2(leerPagos(cont).reduce((s, p) => s + p.monto, 0));
            const resta = r2(total - suma);
            const r = el.querySelector('#c-restante');
            r.textContent = resta > 0 ? `Quedará pendiente: ${dinero(resta)}` : resta < 0 ? `Excede el total por ${dinero(-resta)}` : 'Pago completo';
            r.className = 'small ' + (resta < 0 ? 'text-danger' : resta > 0 ? 'text-warning-emphasis' : 'text-success');
        };
        const actualizar = enlazarPagos(cont, totalActual, refrescar);
        cont.appendChild(filaPago(subtotal));
        el.querySelector('#c-agregar').addEventListener('click', () => {
            const suma = r2(leerPagos(cont).reduce((s, p) => s + p.monto, 0));
            cont.appendChild(filaPago(Math.max(0, r2(totalActual() - suma))));
            actualizar();
        });
        el.querySelector('#c-descuento').addEventListener('input', () => {
            const filas = cont.querySelectorAll('.fila-pago');
            if (filas.length === 1) filas[0].querySelector('[data-c=monto]').value = totalActual().toFixed(2);
            actualizar();
        });
        el.querySelector('#c-pendiente').addEventListener('change', refrescar);
        actualizar();
        el.addEventListener('hidden.bs.modal', () => resolve(resultado));
    });
}

/** Registra un pago (total o parcial) de una venta pendiente. Devuelve la venta actualizada. */
export async function registrarPago(venta) {
    const clave = nuevaClave();
    const cajaAbierta = await estadoCaja();
    return new Promise(resolve => {
        let resultado = null;
        const m = modal({
            titulo: 'Registrar pago de ' + venta.numero,
            cuerpo: `${cajaAbierta === false ? `<div class="alert alert-warning small">La caja está cerrada. Ábrala en <a href="/caja.html">Caja</a> para recibir pagos.</div>` : ''}
                <div class="d-flex justify-content-between mb-3"><span>Saldo pendiente</span><strong class="fs-4">${dinero(venta.saldo)}</strong></div>
                <div id="c-pagos"></div>`,
            botones: [
                { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
                { texto: 'Registrar pago', clase: 'btn-primary', accion: async md => {
                    const [p] = leerPagos(md.el.querySelector('#c-pagos'));
                    validarPagos([p], venta.saldo);
                    resultado = await api(`/api/ventas/${venta.id}/pagos`, { datos: p, clave });
                    aviso('Pago registrado');
                    md.cerrar();
                } }
            ]
        });
        const cont = m.el.querySelector('#c-pagos');
        cont.appendChild(filaPago(Number(venta.saldo)));
        cont.querySelector('[data-quitar]').remove();
        enlazarPagos(cont, () => venta.saldo, () => {})();
        m.el.addEventListener('hidden.bs.modal', () => resolve(resultado));
    });
}

/** Muestra el resultado de una venta y ofrece abrir el comprobante para imprimir. */
export function ventaRegistrada(venta) {
    const cambio = (venta.pagos || []).reduce((s, p) => s + Number(p.cambio || 0), 0);
    const avisoReceta = venta.productosSinReceta && venta.productosSinReceta.length
        ? `<div class="alert alert-warning small mt-3 mb-0"><i class="bi bi-info-circle"></i> Sin descuento automático de inventario
            (receta no configurada): ${venta.productosSinReceta.map(esc).join(', ')}.</div>` : '';
    modal({
        titulo: 'Venta ' + venta.numero + ' registrada',
        cuerpo: `<div class="text-center">
            <div class="display-6 fw-bold">${dinero(venta.total)}</div>
            <div class="mb-2">${venta.estado === 'PAGADA' ? '<span class="estado estado-PAGADA">Pagada</span>'
                : `<span class="estado estado-PENDIENTE_PAGO">Pendiente: ${dinero(venta.saldo)}</span>`}</div>
            ${cambio > 0 ? `<div class="fs-4">Cambio: <strong>${dinero(cambio)}</strong></div>` : ''}
            <div class="text-secondary small mt-2">Comprobante interno ${esc(venta.comprobanteNumero || '')}</div></div>${avisoReceta}`,
        botones: [
            { texto: 'Cerrar', clase: 'btn-outline-secondary', cerrar: true },
            { texto: 'Ver e imprimir comprobante', clase: 'btn-primary', accion: async () => {
                location.href = '/comprobante.html?id=' + venta.comprobanteId + '&imprimir=1';
            } }
        ]
    });
}
