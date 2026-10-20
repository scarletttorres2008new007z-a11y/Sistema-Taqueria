// Utilidades de interfaz: formatos, avisos, confirmaciones, tablas y paginacion.
import { nuevaClave } from './api.js';

const ZONA = 'America/El_Salvador';

export function esc(texto) {
    if (texto === null || texto === undefined) return '';
    return String(texto).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const formatoDinero = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export function dinero(valor) {
    const n = Number(valor || 0);
    return (n < 0 ? '-$' : '$') + formatoDinero.format(Math.abs(n));
}

export function numero(valor, decimales = 3) {
    const n = Number(valor || 0);
    return n.toLocaleString('en-US', { maximumFractionDigits: decimales });
}

/** Fecha dd/mm/aaaa. Acepta "2026-10-20" o un instante ISO. */
export function fecha(valor) {
    if (!valor) return '';
    if (/^\d{4}-\d{2}-\d{2}$/.test(valor)) {
        const [a, m, d] = valor.split('-');
        return `${d}/${m}/${a}`;
    }
    return new Intl.DateTimeFormat('es-SV', { timeZone: ZONA, day: '2-digit', month: '2-digit', year: 'numeric' })
        .format(new Date(valor));
}

/** Fecha y hora dd/mm/aaaa hh:mm en hora de El Salvador. */
export function fechaHora(valor) {
    if (!valor) return '';
    const d = new Date(valor);
    const f = new Intl.DateTimeFormat('es-SV', { timeZone: ZONA, day: '2-digit', month: '2-digit', year: 'numeric' }).format(d);
    const h = new Intl.DateTimeFormat('es-SV', { timeZone: ZONA, hour: '2-digit', minute: '2-digit', hour12: false }).format(d);
    return `${f} ${h}`;
}

export function hora(valor) {
    if (!valor) return '';
    return new Intl.DateTimeFormat('es-SV', { timeZone: ZONA, hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(valor));
}

/** Fecha de hoy en El Salvador como aaaa-mm-dd (para campos type=date). */
export function hoyISO(desplazamientoDias = 0) {
    const d = new Date(Date.now() + desplazamientoDias * 86400000);
    const partes = new Intl.DateTimeFormat('en-CA', { timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(d);
    const v = t => partes.find(p => p.type === t).value;
    return `${v('year')}-${v('month')}-${v('day')}`;
}

export function minutosDesde(valor) {
    return Math.max(0, Math.floor((Date.now() - new Date(valor).getTime()) / 60000));
}

export const NOMBRES = {
    PENDIENTE: 'Pendiente', EN_PREPARACION: 'En preparación', LISTO: 'Listo para entregar', ENTREGADO: 'Entregado',
    CANCELADO: 'Cancelado', PENDIENTE_PAGO: 'Pendiente de pago', PAGADA: 'Pagada', ANULADA: 'Anulada', ANULADO: 'Anulado',
    VIGENTE: 'Vigente', APLICADO: 'Aplicado', REVERTIDO: 'Devuelto', ABIERTA: 'Abierta', CERRADA: 'Cerrada',
    PENDIENTE_RECEPCION: 'Por recibir', RECIBIDA: 'Recibida', PAGADA_COMPRA: 'Pagada', PARCIAL: 'Pago parcial',
    LOCAL: 'Consumo en el local', LLEVAR: 'Para llevar', DOMICILIO: 'A domicilio',
    EFECTIVO: 'Efectivo', TARJETA: 'Tarjeta', TRANSFERENCIA: 'Transferencia',
    PREPARADO: 'Preparado', REVENTA: 'Reventa', COMBO: 'Combo',
    INGREDIENTE: 'Ingrediente', PRODUCTO_TERMINADO: 'Producto terminado', MATERIAL: 'Material',
    ENTRADA: 'Entrada', SALIDA: 'Salida', AJUSTE: 'Ajuste', VENTA: 'Venta', DEVOLUCION_VENTA: 'Devolución de venta',
    COMPRA: 'Compra', DEVOLUCION: 'Devolución', INGRESO: 'Entrada de efectivo', RETIRO: 'Retiro', GASTO: 'Gasto',
    PAGO_PROVEEDOR: 'Pago a proveedor', AGOTADO: 'Agotado', BAJO: 'Bajo', OK: 'Normal', VENCIDO: 'Vencido', POR_VENCER: 'Por vencer'
};

export function nombre(codigo) {
    return NOMBRES[codigo] || codigo || '';
}

export function estado(codigo, texto) {
    if (!codigo) return '';
    return `<span class="estado estado-${esc(codigo)}">${esc(texto || nombre(codigo))}</span>`;
}

// ---------- Avisos ----------

function zonaAvisos() {
    let z = document.querySelector('.zona-avisos');
    if (!z) {
        z = document.createElement('div');
        z.className = 'zona-avisos';
        z.setAttribute('aria-live', 'polite');
        document.body.appendChild(z);
    }
    return z;
}

export function aviso(mensaje, tipo = 'exito') {
    const colores = { exito: 'success', error: 'danger', info: 'secondary', alerta: 'warning' };
    const iconos = { exito: 'check-circle-fill', error: 'exclamation-octagon-fill', info: 'info-circle-fill', alerta: 'exclamation-triangle-fill' };
    const el = document.createElement('div');
    el.className = `alert alert-${colores[tipo] || 'secondary'} shadow-sm mb-0 d-flex gap-2 align-items-start`;
    el.setAttribute('role', tipo === 'error' ? 'alert' : 'status');
    el.innerHTML = `<i class="bi bi-${iconos[tipo]}"></i><div class="flex-grow-1">${esc(mensaje)}</div>
        <button type="button" class="btn-close btn-sm" aria-label="Cerrar"></button>`;
    el.querySelector('.btn-close').addEventListener('click', () => el.remove());
    zonaAvisos().appendChild(el);
    setTimeout(() => el.remove(), tipo === 'error' ? 9000 : 4500);
}

export function avisoError(e) {
    aviso(e && e.message ? e.message : 'Ocurrió un error inesperado', 'error');
}

// ---------- Modales ----------

let contadorModal = 0;

/**
 * Muestra un modal con contenido HTML. Devuelve { el, cerrar, cuerpo }.
 * botones: [{ texto, clase, accion(modal) -> Promise }]
 */
export function modal({ titulo, cuerpo, botones = [], tamano = '' }) {
    const id = 'modal-' + (++contadorModal);
    const el = document.createElement('div');
    el.className = 'modal fade';
    el.id = id;
    el.tabIndex = -1;
    el.setAttribute('aria-labelledby', id + '-titulo');
    el.innerHTML = `<div class="modal-dialog modal-dialog-scrollable ${tamano}"><div class="modal-content">
        <div class="modal-header"><h2 class="modal-title fs-5" id="${id}-titulo">${esc(titulo)}</h2>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Cerrar"></button></div>
        <div class="modal-body"></div><div class="modal-footer"></div></div></div>`;
    const cuerpoEl = el.querySelector('.modal-body');
    if (typeof cuerpo === 'string') cuerpoEl.innerHTML = cuerpo; else if (cuerpo) cuerpoEl.appendChild(cuerpo);
    document.body.appendChild(el);
    const instancia = new bootstrap.Modal(el);
    const resultado = { el, cuerpo: cuerpoEl, cerrar: () => instancia.hide() };
    const pie = el.querySelector('.modal-footer');
    if (!botones.length) pie.remove();
    botones.forEach(b => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn ' + (b.clase || 'btn-secondary');
        btn.textContent = b.texto;
        if (b.cerrar) btn.setAttribute('data-bs-dismiss', 'modal');
        if (b.accion) btn.addEventListener('click', () => ocupado(btn, () => b.accion(resultado)));
        pie.appendChild(btn);
    });
    el.addEventListener('hidden.bs.modal', () => el.remove());
    instancia.show();
    return resultado;
}

/** Pide confirmacion antes de una operacion importante. */
export function confirmar(mensaje, { titulo = 'Confirmar', textoBoton = 'Confirmar', peligro = false } = {}) {
    return new Promise(resolve => {
        let aceptado = false;
        const m = modal({
            titulo,
            cuerpo: `<p class="mb-0">${esc(mensaje)}</p>`,
            botones: [
                { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
                { texto: textoBoton, clase: peligro ? 'btn-danger' : 'btn-primary', accion: async md => { aceptado = true; md.cerrar(); } }
            ]
        });
        m.el.addEventListener('hidden.bs.modal', () => resolve(aceptado));
    });
}

/** Pide un texto obligatorio (por ejemplo, el motivo de una cancelacion). */
export function pedirTexto(mensaje, { titulo = 'Motivo', etiqueta = 'Motivo', textoBoton = 'Aceptar', peligro = false, extra = '' } = {}) {
    return new Promise(resolve => {
        let valor = null;
        const m = modal({
            titulo,
            cuerpo: `<p>${esc(mensaje)}</p><label class="form-label" for="texto-modal">${esc(etiqueta)}</label>
                <textarea id="texto-modal" class="form-control" rows="2" maxlength="250" required></textarea>${extra}`,
            botones: [
                { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
                {
                    texto: textoBoton, clase: peligro ? 'btn-danger' : 'btn-primary', accion: async md => {
                        const t = md.el.querySelector('#texto-modal').value.trim();
                        if (!t) { aviso('Escriba el ' + etiqueta.toLowerCase(), 'alerta'); return; }
                        valor = { texto: t, el: md.el };
                        md.cerrar();
                    }
                }
            ]
        });
        m.el.addEventListener('shown.bs.modal', () => m.el.querySelector('#texto-modal').focus());
        m.el.addEventListener('hidden.bs.modal', () => resolve(valor));
    });
}

/** Ejecuta una accion deshabilitando el boton para evitar doble clic. */
export async function ocupado(boton, accion) {
    if (boton.disabled) return;
    const original = boton.innerHTML;
    boton.disabled = true;
    boton.innerHTML = `<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>${esc(boton.textContent.trim() || 'Procesando')}`;
    try {
        return await accion();
    } catch (e) {
        avisoError(e);
    } finally {
        boton.disabled = false;
        boton.innerHTML = original;
    }
}

// ---------- Estados de carga y tablas ----------

export function cargando(el, texto = 'Cargando...') {
    el.innerHTML = `<div class="cargando"><span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>${esc(texto)}</div>`;
}

export function vacio(texto, icono = 'inbox') {
    return `<div class="vacio"><i class="bi bi-${icono}"></i>${esc(texto)}</div>`;
}

/** Controles de paginacion. alCambiar(nuevaPagina). */
export function paginacion(el, { pagina, totalPaginas, totalElementos }, alCambiar) {
    if (!el) return;
    if (!totalElementos) { el.innerHTML = ''; return; }
    el.innerHTML = `<span class="text-secondary small">${totalElementos} registro(s) · página ${pagina + 1} de ${Math.max(totalPaginas, 1)}</span>
        <div class="btn-group btn-group-sm">
            <button class="btn btn-outline-secondary" data-p="${pagina - 1}" ${pagina <= 0 ? 'disabled' : ''}><i class="bi bi-chevron-left"></i> Anterior</button>
            <button class="btn btn-outline-secondary" data-p="${pagina + 1}" ${pagina + 1 >= totalPaginas ? 'disabled' : ''}>Siguiente <i class="bi bi-chevron-right"></i></button>
        </div>`;
    el.querySelectorAll('button[data-p]').forEach(b => b.addEventListener('click', () => alCambiar(Number(b.dataset.p))));
}

/**
 * Tabla con busqueda y paginacion en el navegador para listas pequenas (catalogos).
 * filas: arreglo de objetos; fila(obj) -> '<tr>...'; filtro(obj, texto) -> boolean
 */
export function tablaLocal({ cuerpo, pie, buscador, filas, fila, filtro, vacioTexto = 'No hay registros', porPagina = 15 }) {
    let pagina = 0;
    const dibujar = () => {
        const texto = (buscador && buscador.value || '').trim().toLowerCase();
        const filtradas = texto ? filas.filter(f => filtro(f, texto)) : filas;
        const totalPaginas = Math.ceil(filtradas.length / porPagina);
        if (pagina >= totalPaginas) pagina = Math.max(0, totalPaginas - 1);
        const visibles = filtradas.slice(pagina * porPagina, (pagina + 1) * porPagina);
        const columnas = cuerpo.closest('table').querySelectorAll('thead th').length || 1;
        cuerpo.innerHTML = visibles.length ? visibles.map(fila).join('')
            : `<tr><td colspan="${columnas}">${vacio(texto ? 'Ningún registro coincide con la búsqueda' : vacioTexto)}</td></tr>`;
        paginacion(pie, { pagina, totalPaginas, totalElementos: filtradas.length }, p => { pagina = p; dibujar(); });
    };
    if (buscador) buscador.oninput = () => { pagina = 0; dibujar(); };
    dibujar();
    return { actualizar: nuevas => { filas = nuevas; dibujar(); } };
}

/** Lee los campos de un formulario como objeto (checkbox -> boolean, number -> numero o null). */
export function leerFormulario(form) {
    const datos = {};
    form.querySelectorAll('[name]').forEach(el => {
        if (el.type === 'checkbox') datos[el.name] = el.checked;
        else if (el.type === 'number') datos[el.name] = el.value === '' ? null : Number(el.value);
        else if (el.tagName === 'SELECT' && el.dataset.numero !== undefined) datos[el.name] = el.value === '' ? null : Number(el.value);
        else datos[el.name] = el.value.trim() === '' ? null : el.value.trim();
    });
    return datos;
}

/** Carga valores en los campos de un formulario. */
export function llenarFormulario(form, obj) {
    form.querySelectorAll('[name]').forEach(el => {
        const v = obj[el.name];
        if (el.type === 'checkbox') el.checked = !!v;
        else el.value = v === null || v === undefined ? '' : v;
    });
}

/** Marca los campos con error devueltos por el servidor. */
export function marcarErrores(form, campos) {
    form.querySelectorAll('.is-invalid').forEach(e => e.classList.remove('is-invalid'));
    Object.keys(campos || {}).forEach(c => {
        const el = form.querySelector(`[name="${c}"]`);
        if (el) el.classList.add('is-invalid');
    });
}

export function opciones(lista, valor, texto, seleccionado, vacioTexto) {
    return (vacioTexto !== undefined ? `<option value="">${esc(vacioTexto)}</option>` : '') +
        lista.map(x => `<option value="${esc(x[valor])}" ${String(x[valor]) === String(seleccionado) ? 'selected' : ''}>${esc(typeof texto === 'function' ? texto(x) : x[texto])}</option>`).join('');
}

export function parametro(nombre) {
    return new URLSearchParams(location.search).get(nombre);
}

/**
 * Modal con formulario. html: campos con atributo name; valores: objeto para llenar;
 * guardar(datos, form, clave) -> Promise. clave es una clave de idempotencia creada al abrir el
 * formulario: si se envia dos veces, el servidor no repite la operacion.
 * Si el servidor devuelve errores por campo, se marcan.
 */
export function formulario({ titulo, html, valores = {}, guardar, tamano = '', textoBoton = 'Guardar', alAbrir }) {
    const clave = nuevaClave();
    const m = modal({
        titulo, tamano,
        cuerpo: `<form novalidate>${html}<button type="submit" hidden></button></form>`,
        botones: [
            { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
            { texto: textoBoton, clase: 'btn-primary', accion: async md => {
                const form = md.el.querySelector('form');
                try {
                    await guardar(leerFormulario(form), form, clave);
                    md.cerrar();
                } catch (e) {
                    marcarErrores(form, e.campos);
                    throw e;
                }
            } }
        ]
    });
    const form = m.el.querySelector('form');
    llenarFormulario(form, valores);
    form.addEventListener('submit', ev => {
        ev.preventDefault();
        m.el.querySelector('.modal-footer .btn-primary').click();
    });
    if (alAbrir) alAbrir(form, m);
    m.el.addEventListener('shown.bs.modal', () => {
        const primero = form.querySelector('input:not([type=hidden]):not([type=checkbox]), select, textarea');
        if (primero) primero.focus();
    });
    return m;
}

/** Campo de formulario con etiqueta. */
export function campo(nombre, etiqueta, { tipo = 'text', col = 'col-12', extra = '', ayuda = '', opcionesHtml = null } = {}) {
    const id = 'campo-' + nombre;
    if (tipo === 'checkbox') {
        return `<div class="${col}"><div class="form-check form-switch mt-2"><input class="form-check-input" type="checkbox" id="${id}" name="${nombre}" ${extra}>
            <label class="form-check-label" for="${id}">${esc(etiqueta)}</label></div>${ayuda ? `<div class="form-text">${ayuda}</div>` : ''}</div>`;
    }
    let control;
    if (opcionesHtml !== null) control = `<select class="form-select" id="${id}" name="${nombre}" ${extra}>${opcionesHtml}</select>`;
    else if (tipo === 'textarea') control = `<textarea class="form-control" id="${id}" name="${nombre}" rows="2" ${extra}></textarea>`;
    else control = `<input class="form-control" type="${tipo}" id="${id}" name="${nombre}" ${extra}>`;
    return `<div class="${col}"><label class="form-label small mb-1" for="${id}">${esc(etiqueta)}</label>${control}
        ${ayuda ? `<div class="form-text">${ayuda}</div>` : ''}<div class="invalid-feedback">Revise este campo</div></div>`;
}
