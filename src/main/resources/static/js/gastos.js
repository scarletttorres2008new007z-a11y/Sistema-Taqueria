// Gastos operativos con categorias configurables, comprobante opcional y anulacion con motivo.
import { api, consulta } from './comun/api.js';
import { esc, dinero, fecha, nombre, aviso, formulario, campo, opciones, vacio, pedirTexto, hoyISO, modal } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Gastos', permiso: 'gastos.ver' });
const $ = id => document.getElementById(id);
const gestiona = tiene('gastos.gestionar');
const configura = tiene('configuracion.gestionar');
if (!gestiona) document.querySelectorAll('.gestionar').forEach(b => b.classList.add('d-none'));
if (!configura) $('nueva-categoria').classList.add('d-none');

let categorias = [], gastos = [], nombres = {};
const form = $('filtros');
form.desde.value = hoyISO().slice(0, 8) + '01';
form.hasta.value = hoyISO();

async function cargarCategorias() {
    categorias = await api('/api/categorias-gasto');
    $('f-categoria').innerHTML = opciones(categorias, 'id', 'nombre', $('f-categoria').value, 'Todas');
    $('categorias').innerHTML = categorias.map(c => `<li class="list-group-item d-flex align-items-center small ${c.activa ? '' : 'text-secondary'}">
        ${esc(c.nombre)}${c.activa ? '' : ' (inactiva)'}${configura ? `<button class="btn btn-sm btn-link ms-auto p-0" data-cat="${c.id}">Editar</button>` : ''}</li>`).join('');
}

async function cargar() {
    if (!Object.keys(nombres).length) nombres = await api('/api/usuarios/nombres').catch(() => ({}));
    gastos = await api('/api/gastos' + consulta({ desde: form.desde.value, hasta: form.hasta.value, categoriaId: form.categoriaId.value }));
    $('filas').innerHTML = gastos.length ? gastos.map(g => `<tr class="${g.anulado ? 'text-decoration-line-through text-secondary' : ''}">
        <td>${fecha(g.fecha)}</td><td>${esc(g.categoria.nombre)}</td>
        <td>${esc(g.concepto)}${g.anulado ? `<div class="small text-danger" style="text-decoration:none">Anulado: ${esc(g.motivoAnulacion)}</div>` : ''}</td>
        <td class="small">${esc(nombre(g.metodoPago))}${g.pagadoDesdeCaja ? '<br><span class="badge text-bg-light border">Desde caja</span>' : ''}</td>
        <td class="small">${esc(g.numeroComprobante || '')}${g.comprobanteUrl ? ` <a href="${esc(g.comprobanteUrl)}" target="_blank" rel="noopener">Ver archivo</a>` : ''}</td>
        <td class="small">${esc(nombres[g.usuarioId] || '')}</td>
        <td class="num">${dinero(g.monto)}</td>
        <td class="text-end text-nowrap">${gestiona && !g.anulado ? `<button class="btn btn-sm btn-outline-secondary" data-adjuntar="${g.id}" title="Adjuntar comprobante" aria-label="Adjuntar comprobante"><i class="bi bi-paperclip"></i></button>
            <button class="btn btn-sm btn-outline-danger" data-anular="${g.id}">Anular</button>` : ''}</td></tr>`).join('')
        : `<tr><td colspan="8">${vacio('No hay gastos en este período', 'wallet2')}</td></tr>`;
    const vigentes = gastos.filter(g => !g.anulado);
    const total = vigentes.reduce((s, g) => s + Number(g.monto), 0);
    $('total').textContent = dinero(total);
    const porCat = {};
    vigentes.forEach(g => { porCat[g.categoria.nombre] = (porCat[g.categoria.nombre] || 0) + Number(g.monto); });
    const lista = Object.entries(porCat).sort((a, b) => b[1] - a[1]);
    $('por-categoria').innerHTML = lista.length ? lista.map(([c, v]) => `<div class="mb-2"><div class="d-flex justify-content-between small"><span>${esc(c)}</span><span>${dinero(v)}</span></div>
        <div class="progress" style="height:6px" role="progressbar" aria-label="${esc(c)}"><div class="progress-bar" style="width:${(v / total * 100).toFixed(1)}%;background:var(--color-primario)"></div></div></div>`).join('')
        : '<span class="small text-secondary">Sin datos</span>';
}

$('nuevo').addEventListener('click', () => {
    formulario({
        titulo: 'Registrar gasto', tamano: 'modal-lg',
        html: `<div class="row g-2">
            ${campo('fecha', 'Fecha', { tipo: 'date', col: 'col-md-4', extra: `required max="${hoyISO()}"` })}
            ${campo('categoriaId', 'Categoría', { col: 'col-md-4', extra: 'data-numero required', opcionesHtml: opciones(categorias.filter(c => c.activa), 'id', 'nombre', null, 'Seleccione') })}
            ${campo('monto', 'Monto ($)', { tipo: 'number', col: 'col-md-4', extra: 'min="0.01" step="0.01" required' })}
            ${campo('concepto', 'Concepto', { extra: 'maxlength="200" required', ayuda: 'Ej.: recarga de gas 25 lb' })}
            ${campo('metodoPago', 'Forma de pago', { col: 'col-md-4', opcionesHtml: '<option value="EFECTIVO">Efectivo</option><option value="TARJETA">Tarjeta</option><option value="TRANSFERENCIA">Transferencia</option>' })}
            ${campo('pagadoDesdeCaja', 'Pagado con dinero de la caja abierta', { tipo: 'checkbox', col: 'col-md-8', ayuda: 'Se descuenta del efectivo esperado de la caja (solo efectivo).' })}
            ${campo('numeroComprobante', 'N.º de factura o recibo (opcional)', { col: 'col-md-6', extra: 'maxlength="60"' })}
            <div class="col-md-6"><label class="form-label small mb-1" for="archivo-gasto">Foto o PDF del comprobante (opcional)</label>
                <input class="form-control" type="file" id="archivo-gasto" accept="image/png,image/jpeg,image/webp,application/pdf"></div></div>`,
        valores: { fecha: hoyISO(), metodoPago: 'EFECTIVO' },
        alAbrir: f => {
            const ver = () => { const ef = f.metodoPago.value === 'EFECTIVO'; f.pagadoDesdeCaja.disabled = !ef; if (!ef) f.pagadoDesdeCaja.checked = false; };
            f.metodoPago.addEventListener('change', ver);
            ver();
        },
        guardar: async (datos, f, clave) => {
            const g = await api('/api/gastos', { datos, clave });
            const archivo = f.querySelector('#archivo-gasto');
            if (archivo.files.length) await subir(g.id, archivo.files[0]);
            aviso('Gasto registrado');
            cargar();
        }
    });
});

async function subir(id, archivo) {
    const fd = new FormData();
    fd.append('archivo', archivo);
    await api(`/api/gastos/${id}/comprobante`, { formulario: fd });
}

$('filas').addEventListener('click', async ev => {
    const an = ev.target.closest('[data-anular]');
    const ad = ev.target.closest('[data-adjuntar]');
    if (an) {
        const g = gastos.find(x => x.id === Number(an.dataset.anular));
        const r = await pedirTexto(`Anular el gasto "${g.concepto}" por ${dinero(g.monto)}.${g.pagadoDesdeCaja ? ' El efectivo se devolverá a la caja abierta.' : ''}`, { titulo: 'Anular gasto', textoBoton: 'Anular', peligro: true });
        if (!r) return;
        try { await api(`/api/gastos/${g.id}/anular`, { datos: { motivo: r.texto } }); aviso('Gasto anulado'); cargar(); } catch (e) { aviso(e.message, 'error'); }
    }
    if (ad) {
        const m = modal({ titulo: 'Adjuntar comprobante', cuerpo: `<input class="form-control" type="file" id="arch" accept="image/png,image/jpeg,image/webp,application/pdf" aria-label="Archivo">
            <div class="form-text">PNG, JPG, WebP o PDF de hasta 5 MB.</div>`,
            botones: [{ texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true }, { texto: 'Subir', clase: 'btn-primary', accion: async md => {
                const f = md.el.querySelector('#arch');
                if (!f.files.length) throw new Error('Seleccione un archivo');
                await subir(Number(ad.dataset.adjuntar), f.files[0]);
                aviso('Comprobante adjuntado'); md.cerrar(); cargar();
            } }] });
        m.el.querySelector('#arch').focus();
    }
});

function editarCategoria(c) {
    formulario({
        titulo: c ? 'Editar categoría de gasto' : 'Nueva categoría de gasto',
        html: `<div class="row g-2">${campo('nombre', 'Nombre', { extra: 'maxlength="80" required' })}${campo('activa', 'Activa', { tipo: 'checkbox' })}</div>`,
        valores: c || { activa: true },
        guardar: async datos => {
            await api('/api/categorias-gasto' + (c ? '/' + c.id : ''), { metodo: c ? 'PUT' : 'POST', datos });
            aviso('Categoría guardada');
            cargarCategorias();
        }
    });
}
$('nueva-categoria').addEventListener('click', () => editarCategoria(null));
$('categorias').addEventListener('click', ev => {
    const b = ev.target.closest('[data-cat]');
    if (b) editarCategoria(categorias.find(c => c.id === Number(b.dataset.cat)));
});
form.addEventListener('submit', ev => { ev.preventDefault(); cargar().catch(e => aviso(e.message, 'error')); });

await cargarCategorias().catch(e => aviso(e.message, 'error'));
cargar().catch(e => aviso(e.message, 'error'));
