// Recetas: ingredientes por unidad de producto, con conversion de unidades del mismo tipo.
import { api } from './comun/api.js';
import { esc, dinero, numero, estado, aviso, confirmar, modal, opciones, tablaLocal, parametro, fechaHora } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Recetas', permiso: 'productos.ver' });
const $ = id => document.getElementById(id);
const gestiona = tiene('recetas.gestionar');
let resumen = [], items = [], unidades = [];
let tabla;
const factorIva = await api('/api/configuracion').then(c => 1 + Number(c.ivaPorcentaje) / 100).catch(() => 1.13);

const ETIQUETA = { RECETA: ['OK', 'Por receta'], REVENTA: ['EN_PREPARACION', 'Artículo de reventa'], SIN_CONFIGURAR: ['PENDIENTE', 'Receta no configurada'] };

async function cargar() {
    resumen = await api('/api/recetas');
    if (!unidades.length) unidades = await api('/api/unidades');
    if (gestiona && !items.length) items = (await api('/api/inventario')).filter(i => i.activo);
    const filas = () => resumen.filter(r => r.activo && (!$('filtro').value || r.descuentoAutomatico === $('filtro').value));
    const fila = r => {
        const [cod, txt] = ETIQUETA[r.descuentoAutomatico];
        const costo = r.costoReceta !== null ? Number(r.costoReceta) : null;
        const sinIva = Number(r.precio) / factorIva;
        return `<tr><td class="fw-semibold">${esc(r.nombre)}</td><td>${esc(r.categoria)}</td><td class="num">${dinero(r.precio)}</td>
            <td>${estado(cod, txt)}</td><td class="num">${r.descuentoAutomatico === 'REVENTA' ? '' : r.ingredientes}</td>
            <td class="num">${costo !== null ? dinero(costo) : ''}</td>
            <td class="num">${costo !== null && sinIva > 0 ? Math.round((sinIva - costo) / sinIva * 100) + ' %' : ''}</td>
            <td class="text-end">${r.descuentoAutomatico !== 'REVENTA' ? `<button class="btn btn-sm btn-outline-primary" data-id="${r.productoId}">${gestiona ? 'Editar receta' : 'Ver'}</button>` : ''}</td></tr>`;
    };
    if (tabla) tabla.actualizar(filas());
    else tabla = tablaLocal({ cuerpo: $('filas'), pie: $('pie'), buscador: $('buscar'), filas: filas(), fila,
        filtro: (r, t) => r.nombre.toLowerCase().includes(t), vacioTexto: 'No hay productos activos' });
    $('filtro').onchange = () => tabla.actualizar(filas());
    const sin = resumen.filter(r => r.activo && r.descuentoAutomatico === 'SIN_CONFIGURAR').length;
    $('contador').innerHTML = sin ? `<span class="text-warning-emphasis"><i class="bi bi-exclamation-triangle"></i> ${sin} producto(s) sin receta</span>` : '<span class="text-success">Todos los productos descuentan inventario</span>';
}

function unidadesCompatibles(item) {
    return item ? unidades.filter(u => u.tipo === item.unidad.tipo) : unidades;
}

function filaDetalle(d = {}) {
    const item = items.find(i => i.id === (d.item ? d.item.id : null));
    return `<tr>
        <td><select class="form-select form-select-sm" data-c="item" aria-label="Ingrediente">${opciones(items, 'id', i => `${i.nombre} (${i.unidad.codigo})`, item ? item.id : '', 'Seleccione')}</select></td>
        <td style="width:110px"><input class="form-control form-control-sm" type="number" min="0.0001" step="0.0001" data-c="cantidad" value="${d.cantidad ?? ''}" aria-label="Cantidad"></td>
        <td style="width:120px"><select class="form-select form-select-sm" data-c="unidad" aria-label="Unidad">${opciones(unidadesCompatibles(item), 'id', 'codigo', d.unidad ? d.unidad.id : (item ? item.unidad.id : ''))}</select></td>
        <td class="num small" data-c="costo"></td>
        <td><button type="button" class="btn btn-sm btn-outline-danger" data-quitar aria-label="Quitar ingrediente"><i class="bi bi-x-lg"></i></button></td></tr>`;
}

function costoFila(tr) {
    const item = items.find(i => i.id === Number(tr.querySelector('[data-c=item]').value));
    const u = unidades.find(x => x.id === Number(tr.querySelector('[data-c=unidad]').value));
    const cant = Number(tr.querySelector('[data-c=cantidad]').value);
    if (!item || !u || !cant) return 0;
    // costo = cantidad convertida a la unidad del articulo x costo unitario
    const enUnidadItem = cant * Number(u.factor) / Number(item.unidad.factor);
    return enUnidadItem * Number(item.costoUnitario);
}

async function editar(productoId) {
    const r = await api('/api/recetas/' + productoId);
    const p = r.producto;
    const detalles = r.configurada ? r.receta.detalles : [];
    if (!gestiona) {
        modal({ titulo: 'Receta de ' + p.nombre, tamano: 'modal-lg', cuerpo: r.configurada
            ? `<table class="table table-sm"><thead><tr><th>Ingrediente</th><th class="num">Cantidad</th></tr></thead><tbody>
                ${detalles.map(d => `<tr><td>${esc(d.item.nombre)}</td><td class="num">${numero(d.cantidad, 4)} ${esc(d.unidad.codigo)}</td></tr>`).join('')}</tbody></table>
                ${r.receta.notas ? `<p class="small">${esc(r.receta.notas)}</p>` : ''}`
            : `<p class="mb-0">${esc(r.mensaje)}</p>` });
        return;
    }
    const m = modal({
        titulo: 'Receta de ' + p.nombre, tamano: 'modal-xl',
        cuerpo: `${!r.configurada ? `<div class="alert alert-warning small">${esc(r.mensaje)}</div>` : `<p class="small text-secondary">Última modificación: ${fechaHora(r.receta.actualizadoEn)} por ${esc(r.receta.actualizadoPor || '')}</p>`}
            <p class="small">Cantidades para <strong>1 unidad</strong> de "${esc(p.nombre)}" (precio ${dinero(p.precio)}). La unidad puede ser distinta a la del inventario si es del mismo tipo (por ejemplo, gramos para un artículo en libras).</p>
            <div class="tabla-contenedor"><table class="table table-sm align-middle"><thead><tr><th>Ingrediente</th><th>Cantidad</th><th>Unidad</th><th class="num">Costo</th><th></th></tr></thead>
            <tbody id="r-filas">${detalles.map(filaDetalle).join('')}</tbody>
            <tfoot><tr><td colspan="3"><button type="button" class="btn btn-sm btn-outline-secondary" id="r-agregar"><i class="bi bi-plus"></i> Agregar ingrediente</button></td>
                <th class="num" id="r-costo"></th><td></td></tr></tfoot></table></div>
            <label class="form-label small mb-1" for="r-notas">Notas de preparación (opcional)</label>
            <textarea class="form-control" id="r-notas" rows="2" maxlength="500">${esc(r.configurada ? r.receta.notas || '' : '')}</textarea>`,
        botones: [
            ...(r.configurada ? [{ texto: 'Quitar receta', clase: 'btn-outline-danger me-auto', accion: async md => {
                if (!await confirmar('Sin receta, este producto dejará de descontar inventario. ¿Continuar?', { peligro: true, textoBoton: 'Quitar receta' })) return;
                await api('/api/recetas/' + productoId, { metodo: 'DELETE' });
                aviso('Receta eliminada'); md.cerrar(); cargar();
            } }] : []),
            { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
            { texto: 'Guardar receta', clase: 'btn-primary', accion: async md => {
                const filas = [...md.el.querySelectorAll('#r-filas tr')];
                const datos = filas.map(tr => ({
                    itemId: Number(tr.querySelector('[data-c=item]').value) || null,
                    cantidad: Number(tr.querySelector('[data-c=cantidad]').value) || null,
                    unidadId: Number(tr.querySelector('[data-c=unidad]').value) || null
                }));
                if (!datos.length) throw new Error('Agregue al menos un ingrediente o use "Quitar receta"');
                if (datos.some(d => !d.itemId || !d.cantidad || d.cantidad <= 0 || !d.unidadId)) throw new Error('Complete ingrediente, cantidad mayor que cero y unidad en cada fila');
                if (new Set(datos.map(d => d.itemId)).size !== datos.length) throw new Error('Un ingrediente aparece repetido; sume las cantidades en una sola fila');
                await api('/api/recetas/' + productoId, { metodo: 'PUT', datos: { notas: md.el.querySelector('#r-notas').value.trim() || null, detalles: datos } });
                aviso('Receta guardada'); md.cerrar(); cargar();
            } }
        ]
    });
    const cuerpo = m.el.querySelector('#r-filas');
    const recalcular = () => {
        let total = 0;
        cuerpo.querySelectorAll('tr').forEach(tr => { const c = costoFila(tr); total += c; tr.querySelector('[data-c=costo]').textContent = c ? dinero(c) : ''; });
        m.el.querySelector('#r-costo').textContent = 'Costo: ' + dinero(total);
    };
    cuerpo.addEventListener('change', ev => {
        if (ev.target.dataset.c === 'item') {
            const item = items.find(i => i.id === Number(ev.target.value));
            ev.target.closest('tr').querySelector('[data-c=unidad]').innerHTML = opciones(unidadesCompatibles(item), 'id', 'codigo', item ? item.unidad.id : '');
        }
        recalcular();
    });
    cuerpo.addEventListener('input', recalcular);
    cuerpo.addEventListener('click', ev => { if (ev.target.closest('[data-quitar]')) { ev.target.closest('tr').remove(); recalcular(); } });
    m.el.querySelector('#r-agregar').addEventListener('click', () => { cuerpo.insertAdjacentHTML('beforeend', filaDetalle()); recalcular(); });
    if (!detalles.length) cuerpo.insertAdjacentHTML('beforeend', filaDetalle());
    recalcular();
}

$('filas').addEventListener('click', ev => {
    const b = ev.target.closest('[data-id]');
    if (b) editar(Number(b.dataset.id)).catch(e => aviso(e.message, 'error'));
});

await cargar().catch(e => aviso(e.message, 'error'));
if (parametro('producto')) editar(Number(parametro('producto'))).catch(e => aviso(e.message, 'error'));
