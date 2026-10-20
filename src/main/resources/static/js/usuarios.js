// Empleados, cargos y permisos. Las contrasenas se guardan cifradas (BCrypt) en el servidor.
import { api } from './comun/api.js';
import { esc, fechaHora, estado, aviso, formulario, campo, opciones, tablaLocal, modal } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { usuario: yo } = await iniciarPagina({ titulo: 'Empleados y permisos', permiso: 'usuarios.gestionar' });
const $ = id => document.getElementById(id);
let usuarios = [], roles = [], catalogo = {};
let tabla;

async function cargar() {
    [usuarios, roles, catalogo] = await Promise.all([api('/api/usuarios'), api('/api/roles'), api('/api/permisos')]);
    const fila = u => `<tr class="${u.activo ? '' : 'text-secondary'}"><td class="fw-semibold">${esc(u.nombre)} ${u.demo ? '<span class="badge text-bg-warning">Demo</span>' : ''}</td>
        <td>${esc(u.username)}</td><td>${esc(u.rol.nombre)}</td><td>${esc(u.telefono || '')}</td>
        <td class="small">${u.ultimoAcceso ? fechaHora(u.ultimoAcceso) : 'Nunca'}</td>
        <td>${u.activo ? estado('OK', 'Activo') : estado('INACTIVO', 'Inactivo')}</td>
        <td class="text-end text-nowrap"><button class="btn btn-sm btn-outline-secondary" data-clave="${u.id}">Restablecer contraseña</button>
            <button class="btn btn-sm btn-outline-primary" data-id="${u.id}">Editar</button></td></tr>`;
    if (tabla) tabla.actualizar(usuarios);
    else tabla = tablaLocal({ cuerpo: $('filas'), pie: $('pie'), buscador: $('buscar'), filas: usuarios, fila,
        filtro: (u, t) => u.nombre.toLowerCase().includes(t) || u.username.toLowerCase().includes(t) });
    dibujarRoles();
}

function editar(u) {
    formulario({
        titulo: u ? 'Editar ' + u.nombre : 'Nuevo empleado', tamano: 'modal-lg',
        html: `<div class="row g-2">
            ${campo('nombre', 'Nombre completo', { col: 'col-md-6', extra: 'maxlength="120" required' })}
            ${campo('username', 'Usuario para entrar', { col: 'col-md-6', extra: 'maxlength="50" required pattern="[A-Za-z0-9._\\-]+" autocomplete="off"', ayuda: 'Letras, números, punto o guion' })}
            ${campo('rolId', 'Cargo', { col: 'col-md-6', extra: 'data-numero required', opcionesHtml: opciones(roles, 'id', 'nombre', null, 'Seleccione') })}
            ${campo('telefono', 'Teléfono', { col: 'col-md-6', extra: 'maxlength="30"' })}
            ${campo('password', u ? 'Nueva contraseña (dejar vacío para no cambiarla)' : 'Contraseña inicial', { tipo: 'password', col: 'col-md-6', extra: `minlength="8" autocomplete="new-password" ${u ? '' : 'required'}`, ayuda: 'Mínimo 8 caracteres. Entréguela al empleado en persona.' })}
            ${campo('activo', 'Activo (puede iniciar sesión)', { tipo: 'checkbox', col: 'col-md-6', extra: u && u.id === yo.id ? 'disabled' : '' })}</div>`,
        valores: u ? { ...u, rolId: u.rol.id, password: '' } : { activo: true },
        guardar: async (datos, f, clave) => {
            if (u && u.id === yo.id) datos.activo = true;
            await api('/api/usuarios' + (u ? '/' + u.id : ''), { metodo: u ? 'PUT' : 'POST', datos, clave });
            aviso('Empleado guardado');
            cargar();
        }
    });
}

function restablecer(u) {
    formulario({
        titulo: 'Restablecer contraseña de ' + u.nombre, textoBoton: 'Restablecer',
        html: `<div class="row g-2">${campo('nueva', 'Contraseña nueva', { tipo: 'password', extra: 'minlength="8" required autocomplete="new-password"', ayuda: 'Mínimo 8 caracteres. El empleado puede cambiarla luego en "Mi perfil".' })}</div>`,
        guardar: async datos => {
            if (!datos.nueva || datos.nueva.length < 8) throw new Error('La contraseña debe tener al menos 8 caracteres');
            await api(`/api/usuarios/${u.id}/password`, { datos });
            aviso('Contraseña restablecida');
        }
    });
}

$('nuevo').addEventListener('click', () => editar(null));
$('filas').addEventListener('click', ev => {
    const e = ev.target.closest('[data-id]');
    const c = ev.target.closest('[data-clave]');
    if (e) editar(usuarios.find(u => u.id === Number(e.dataset.id)));
    if (c) restablecer(usuarios.find(u => u.id === Number(c.dataset.clave)));
});

// ---------- Cargos y permisos ----------

function grupos() {
    const g = {};
    Object.entries(catalogo).forEach(([codigo, desc]) => {
        const modulo = codigo.split('.')[0];
        (g[modulo] = g[modulo] || []).push([codigo, desc]);
    });
    return g;
}

const NOMBRE_MODULO = { dashboard: 'Panel', reportes: 'Reportes', productos: 'Productos y menú', recetas: 'Recetas', inventario: 'Inventario', pedidos: 'Pedidos',
    ventas: 'Ventas', comprobantes: 'Comprobantes', caja: 'Caja', gastos: 'Gastos', proveedores: 'Proveedores', compras: 'Compras', clientes: 'Clientes',
    usuarios: 'Empleados', configuracion: 'Configuración', auditoria: 'Auditoría' };

function dibujarRoles() {
    $('roles').innerHTML = roles.map(r => `<div class="col-md-6 col-xl-4"><section class="tarjeta h-100">
        <div class="tarjeta-titulo">${esc(r.nombre)} <span class="small text-secondary fw-normal">(${usuarios.filter(u => u.rol.id === r.id && u.activo).length} activo/s)</span>
            ${r.administrador ? '<span class="estado estado-OK ms-auto">Todos los permisos</span>' : `<button class="btn btn-sm btn-outline-primary ms-auto" data-rol="${r.id}">Editar permisos</button>`}</div>
        <div class="tarjeta-cuerpo small">${r.descripcion ? `<p class="text-secondary">${esc(r.descripcion)}</p>` : ''}
            ${r.administrador ? '' : (r.permisos.length ? `<ul class="mb-0 ps-3">${r.permisos.map(p => `<li>${esc(catalogo[p] || p)}</li>`).join('')}</ul>` : '<span class="text-secondary">Sin permisos</span>')}</div></section></div>`).join('');
}

$('roles').addEventListener('click', ev => {
    const b = ev.target.closest('[data-rol]');
    if (!b) return;
    const r = roles.find(x => x.id === Number(b.dataset.rol));
    const g = grupos();
    modal({
        titulo: 'Permisos del cargo ' + r.nombre, tamano: 'modal-lg',
        cuerpo: `<div class="row g-3">${Object.entries(g).map(([m, lista]) => `<div class="col-md-6"><div class="fw-semibold small mb-1">${esc(NOMBRE_MODULO[m] || m)}</div>
            ${lista.map(([codigo, desc]) => `<div class="form-check"><input class="form-check-input" type="checkbox" id="p-${codigo}" value="${codigo}" ${r.permisos.includes(codigo) ? 'checked' : ''}>
            <label class="form-check-label small" for="p-${codigo}">${esc(desc)}</label></div>`).join('')}</div>`).join('')}</div>`,
        botones: [
            { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
            { texto: 'Guardar permisos', clase: 'btn-primary', accion: async md => {
                const permisos = [...md.el.querySelectorAll('.form-check-input:checked')].map(x => x.value);
                await api(`/api/roles/${r.id}/permisos`, { metodo: 'PUT', datos: { permisos } });
                aviso('Permisos actualizados');
                md.cerrar();
                cargar();
            } }
        ]
    });
});

cargar().catch(e => aviso(e.message, 'error'));
