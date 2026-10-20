// Bitacora de auditoria (solo lectura).
import { api, consulta } from './comun/api.js';
import { esc, fechaHora, vacio, paginacion, hoyISO, aviso } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

await iniciarPagina({ titulo: 'Auditoría', permiso: 'auditoria.ver' });
const $ = id => document.getElementById(id);
const form = $('filtros');
form.desde.value = hoyISO(-7);
form.hasta.value = hoyISO();
let pagina = 0;

async function cargar() {
    const r = await api('/api/auditoria' + consulta({ q: form.q.value.trim(), desde: form.desde.value, hasta: form.hasta.value, pagina, tamano: 50 }));
    $('filas').innerHTML = r.contenido.length ? r.contenido.map(a => `<tr><td class="text-nowrap">${fechaHora(a.creadoEn)}</td><td>${esc(a.usuarioNombre || '')}</td>
        <td class="fw-semibold">${esc(a.accion)}</td><td class="small">${esc(a.entidad || '')}${a.entidadId ? ' #' + a.entidadId : ''}</td>
        <td class="small">${esc(a.detalle || '')}</td><td class="small text-secondary">${esc(a.ip || '')}</td></tr>`).join('')
        : `<tr><td colspan="6">${vacio('No hay registros con estos filtros')}</td></tr>`;
    paginacion($('pie'), r, p => { pagina = p; cargar(); });
}
form.addEventListener('submit', ev => { ev.preventDefault(); pagina = 0; cargar().catch(e => aviso(e.message, 'error')); });
cargar().catch(e => aviso(e.message, 'error'));
