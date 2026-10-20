// Clientes (opcional): datos para domicilio y comprobantes, con historial de pedidos y ventas.
import { api, consulta } from './comun/api.js';
import { esc, dinero, fechaHora, estado, aviso, formulario, campo, vacio, paginacion, modal } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Clientes', permiso: 'clientes.ver' });
const $ = id => document.getElementById(id);
const gestiona = tiene('clientes.gestionar');
if (!gestiona) $('nuevo').classList.add('d-none');
const form = $('filtros');
let pagina = 0, lista = [];

async function cargar() {
    const r = await api('/api/clientes' + consulta({ q: form.q.value.trim(), activo: form.activo.value, pagina, tamano: 20 }));
    lista = r.contenido;
    $('filas').innerHTML = lista.length ? lista.map(c => `<tr class="${c.activo ? '' : 'text-secondary'}">
        <td class="fw-semibold">${esc(c.nombre)} ${c.demo ? '<span class="badge text-bg-warning">Demo</span>' : ''}</td><td>${esc(c.telefono || '')}</td>
        <td class="small">${esc(c.direccion || '')}</td><td>${esc(c.documento || '')}</td>
        <td>${c.activo ? estado('OK', 'Activo') : estado('INACTIVO', 'Inactivo')}</td>
        <td class="text-end text-nowrap"><button class="btn btn-sm btn-outline-secondary" data-historial="${c.id}">Historial</button>
            ${gestiona ? `<button class="btn btn-sm btn-outline-primary" data-id="${c.id}">Editar</button>` : ''}</td></tr>`).join('')
        : `<tr><td colspan="6">${vacio('No hay clientes con esta búsqueda', 'people')}</td></tr>`;
    paginacion($('pie'), r, p => { pagina = p; cargar(); });
}

function editar(c) {
    formulario({
        titulo: c ? 'Editar cliente' : 'Nuevo cliente', tamano: 'modal-lg',
        html: `<div class="row g-2">
            ${campo('nombre', 'Nombre', { col: 'col-md-8', extra: 'maxlength="150" required' })}
            ${campo('telefono', 'Teléfono', { col: 'col-md-4', extra: 'maxlength="30" inputmode="tel"' })}
            ${campo('direccion', 'Dirección de entrega', { extra: 'maxlength="250"' })}
            ${campo('correo', 'Correo', { tipo: 'email', col: 'col-md-6', extra: 'maxlength="120"' })}
            ${campo('documento', 'DUI o NIT', { col: 'col-md-3', extra: 'maxlength="30"' })}
            ${campo('nrc', 'NRC', { col: 'col-md-3', extra: 'maxlength="20"' })}
            ${campo('notas', 'Notas (preferencias, referencias)', { tipo: 'textarea', extra: 'maxlength="500"' })}
            ${campo('activo', 'Activo', { tipo: 'checkbox' })}</div>`,
        valores: c || { activo: true },
        guardar: async (datos, f, clave) => {
            await api('/api/clientes' + (c ? '/' + c.id : ''), { metodo: c ? 'PUT' : 'POST', datos, clave });
            aviso('Cliente guardado');
            cargar();
        }
    });
}

async function historial(id) {
    const h = await api(`/api/clientes/${id}/historial`);
    const total = h.ventas.filter(v => v.estado !== 'ANULADA').reduce((s, v) => s + Number(v.total), 0);
    modal({ titulo: 'Historial de ' + h.cliente.nombre, tamano: 'modal-lg', cuerpo: `
        <p>${h.pedidos.length} pedido(s) · ${h.ventas.length} venta(s) · Total comprado: <strong>${dinero(total)}</strong></p>
        <div class="row g-3"><div class="col-md-6"><h3 class="h6">Pedidos</h3>${h.pedidos.length ? `<table class="table table-sm"><tbody>${h.pedidos.map(p => `<tr>
            <td><a href="/pedidos.html?id=${p.id}">${esc(p.numero)}</a></td><td class="small">${fechaHora(p.fecha)}</td><td>${estado(p.estado)}</td><td class="num">${dinero(p.total)}</td></tr>`).join('')}</tbody></table>` : vacio('Sin pedidos')}</div>
        <div class="col-md-6"><h3 class="h6">Ventas</h3>${h.ventas.length ? `<table class="table table-sm"><tbody>${h.ventas.map(v => `<tr>
            <td><a href="/ventas.html?id=${v.id}">${esc(v.numero)}</a></td><td class="small">${fechaHora(v.fecha)}</td><td>${estado(v.estado)}</td><td class="num">${dinero(v.total)}</td></tr>`).join('')}</tbody></table>` : vacio('Sin ventas')}</div></div>` });
}

$('nuevo').addEventListener('click', () => editar(null));
$('filas').addEventListener('click', ev => {
    const e = ev.target.closest('[data-id]');
    const h = ev.target.closest('[data-historial]');
    if (e) editar(lista.find(c => c.id === Number(e.dataset.id)));
    if (h) historial(Number(h.dataset.historial)).catch(err => aviso(err.message, 'error'));
});
form.addEventListener('submit', ev => { ev.preventDefault(); pagina = 0; cargar().catch(e => aviso(e.message, 'error')); });
cargar().catch(e => aviso(e.message, 'error'));
