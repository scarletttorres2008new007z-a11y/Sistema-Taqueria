// Historial de comprobantes internos: busqueda por numero, fecha, cliente y estado.
import { api, consulta } from './comun/api.js';
import { esc, dinero, fechaHora, estado, vacio, paginacion } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

await iniciarPagina({ titulo: 'Historial de comprobantes', permiso: 'comprobantes.ver' });
const $ = id => document.getElementById(id);
const form = $('filtros');
let pagina = 0;

async function cargar() {
    const f = { numero: form.numero.value.trim(), cliente: form.cliente.value.trim(), estado: form.estado.value,
        desde: form.desde.value, hasta: form.hasta.value, pagina, tamano: 20 };
    try {
        const r = await api('/api/comprobantes' + consulta(f));
        $('filas').innerHTML = r.contenido.length ? r.contenido.map(c => `<tr>
            <td class="fw-semibold">${esc(c.numero)}</td><td>${fechaHora(c.fechaEmision)}</td>
            <td><a href="/ventas.html?id=${c.ventaId}">${esc(c.ventaNumero || '')}</a></td>
            <td>${esc(c.clienteNombre || 'Consumidor final')}</td><td class="num">${dinero(c.total)}</td>
            <td>${estado(c.estado)}</td><td class="num">${c.impresiones}</td>
            <td class="text-end"><a class="btn btn-sm btn-outline-primary" href="/comprobante.html?id=${c.id}"><i class="bi bi-printer"></i> Ver / imprimir</a></td></tr>`).join('')
            : `<tr><td colspan="8">${vacio('No hay comprobantes con estos filtros', 'file-earmark-text')}</td></tr>`;
        paginacion($('pie'), r, p => { pagina = p; cargar(); });
        $('nota').textContent = !f.desde && !f.hasta && !f.numero ? 'Mostrando los últimos 30 días.' : '';
    } catch (e) {
        $('filas').innerHTML = `<tr><td colspan="8"><div class="alert alert-danger m-2">${esc(e.message)}</div></td></tr>`;
    }
}
form.addEventListener('submit', ev => { ev.preventDefault(); pagina = 0; cargar(); });
cargar();
