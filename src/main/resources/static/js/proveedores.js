// Proveedores: datos de contacto y fiscales. Se desactivan en lugar de borrarse.
import { api } from './comun/api.js';
import { esc, estado, aviso, formulario, campo, tablaLocal } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

await iniciarPagina({ titulo: 'Proveedores', permiso: 'proveedores.gestionar' });
const $ = id => document.getElementById(id);
let proveedores = [];
let tabla;

async function cargar() {
    proveedores = await api('/api/proveedores');
    const filas = () => proveedores.filter(p => $('ver-inactivos').checked || p.activo);
    const fila = p => `<tr class="${p.activo ? '' : 'text-secondary'}"><td class="fw-semibold">${esc(p.nombreComercial)} ${p.demo ? '<span class="badge text-bg-warning">Demo</span>' : ''}</td>
        <td>${esc(p.contacto || '')}</td><td>${esc(p.telefono || '')}</td><td>${esc(p.correo || '')}</td>
        <td class="small">${esc(p.nit || '')}${p.nrc ? ' / ' + esc(p.nrc) : ''}</td>
        <td>${p.activo ? estado('OK', 'Activo') : estado('INACTIVO', 'Inactivo')}</td>
        <td class="text-end text-nowrap"><a class="btn btn-sm btn-outline-secondary" href="/compras.html">Compras</a>
            <button class="btn btn-sm btn-outline-primary" data-id="${p.id}">Editar</button></td></tr>`;
    if (tabla) tabla.actualizar(filas());
    else tabla = tablaLocal({ cuerpo: $('filas'), pie: $('pie'), buscador: $('buscar'), filas: filas(), fila,
        filtro: (p, t) => [p.nombreComercial, p.contacto, p.telefono].some(x => (x || '').toLowerCase().includes(t)),
        vacioTexto: 'No hay proveedores registrados' });
    $('ver-inactivos').onchange = () => tabla.actualizar(filas());
}

function editar(p) {
    formulario({
        titulo: p ? 'Editar proveedor' : 'Nuevo proveedor', tamano: 'modal-lg',
        html: `<div class="row g-2">
            ${campo('nombreComercial', 'Nombre comercial', { col: 'col-md-6', extra: 'maxlength="150" required' })}
            ${campo('contacto', 'Persona de contacto', { col: 'col-md-6', extra: 'maxlength="120"' })}
            ${campo('telefono', 'Teléfono', { col: 'col-md-4', extra: 'maxlength="30" inputmode="tel"' })}
            ${campo('correo', 'Correo', { tipo: 'email', col: 'col-md-8', extra: 'maxlength="120"' })}
            ${campo('direccion', 'Dirección', { extra: 'maxlength="250"' })}
            ${campo('nit', 'NIT', { col: 'col-md-6', extra: 'maxlength="20"' })}
            ${campo('nrc', 'NRC', { col: 'col-md-6', extra: 'maxlength="20"' })}
            ${campo('notas', 'Notas', { tipo: 'textarea', extra: 'maxlength="500"' })}
            ${campo('activo', 'Activo', { tipo: 'checkbox' })}</div>`,
        valores: p || { activo: true },
        guardar: async (datos, f, clave) => {
            await api('/api/proveedores' + (p ? '/' + p.id : ''), { metodo: p ? 'PUT' : 'POST', datos, clave });
            aviso('Proveedor guardado');
            cargar();
        }
    });
}
$('nuevo').addEventListener('click', () => editar(null));
$('filas').addEventListener('click', ev => {
    const b = ev.target.closest('[data-id]');
    if (b) editar(proveedores.find(p => p.id === Number(b.dataset.id)));
});
cargar().catch(e => aviso(e.message, 'error'));
