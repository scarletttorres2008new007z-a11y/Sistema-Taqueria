// Estructura comun de las paginas internas: verifica la sesion, arma el menu segun
// los permisos del usuario y coloca el contenido de la pagina dentro del marco.

import { api } from './api.js';
import { esc, aviso } from './ui.js';

const MENU = [
    { seccion: null, items: [
        { href: '/panel.html', icono: 'speedometer2', texto: 'Panel principal', permiso: 'dashboard.ver' }
    ] },
    { seccion: 'Operación', items: [
        { href: '/tomar-pedido.html', icono: 'plus-square', texto: 'Tomar pedido', permiso: 'pedidos.crear' },
        { href: '/pedidos.html', icono: 'receipt', texto: 'Pedidos', permiso: 'pedidos.ver' },
        { href: '/cocina.html', icono: 'fire', texto: 'Panel de cocina', permiso: 'pedidos.ver' },
        { href: '/ventas.html', icono: 'cash-coin', texto: 'Ventas', permiso: 'ventas.ver' },
        { href: '/comprobantes.html', icono: 'file-earmark-text', texto: 'Comprobantes', permiso: 'comprobantes.ver' },
        { href: '/caja.html', icono: 'safe', texto: 'Caja', permiso: 'caja.ver' },
        { href: '/gastos.html', icono: 'wallet2', texto: 'Gastos', permiso: 'gastos.ver' }
    ] },
    { seccion: 'Menú e inventario', items: [
        { href: '/productos.html', icono: 'menu-button-wide', texto: 'Productos y menú', permiso: 'productos.ver' },
        { href: '/recetas.html', icono: 'journal-text', texto: 'Recetas', permiso: 'productos.ver' },
        { href: '/inventario.html', icono: 'box-seam', texto: 'Inventario', permiso: 'inventario.ver' }
    ] },
    { seccion: 'Compras y clientes', items: [
        { href: '/compras.html', icono: 'cart-check', texto: 'Compras', permiso: 'compras.ver' },
        { href: '/proveedores.html', icono: 'truck', texto: 'Proveedores', permiso: 'proveedores.gestionar' },
        { href: '/clientes.html', icono: 'people', texto: 'Clientes', permiso: 'clientes.ver' }
    ] },
    { seccion: 'Administración', items: [
        { href: '/reportes.html', icono: 'bar-chart-line', texto: 'Reportes', permiso: 'reportes.ver' },
        { href: '/usuarios.html', icono: 'person-badge', texto: 'Empleados y permisos', permiso: 'usuarios.gestionar' },
        { href: '/auditoria.html', icono: 'shield-check', texto: 'Auditoría', permiso: 'auditoria.ver' },
        { href: '/configuracion.html', icono: 'gear', texto: 'Configuración', permiso: 'configuracion.gestionar' }
    ] }
];

/** Primera pagina a la que puede entrar el usuario segun sus permisos. */
export function paginaInicial(permisos) {
    // El personal de cocina (ve pedidos pero no los toma ni cobra) entra directo al panel de cocina
    if (permisos.includes('pedidos.ver') && !permisos.includes('pedidos.crear') && !permisos.includes('ventas.ver')) return '/cocina.html';
    // El encargado de bodega entra directo al inventario
    if (permisos.includes('inventario.entradas') && !permisos.includes('pedidos.ver')) return '/inventario.html';
    for (const s of MENU) for (const i of s.items) if (permisos.includes(i.permiso)) return i.href;
    return '/perfil.html';
}

/**
 * Inicia una pagina interna.
 * @param {object} op { titulo, permiso } permiso puede ser texto o lista (basta uno)
 * @returns {Promise<{usuario, negocio, tiene: function}>}
 */
export async function iniciarPagina({ titulo, permiso } = {}) {
    let estado;
    try {
        estado = await api('/api/sesion/estado');
    } catch (e) {
        document.body.innerHTML = `<div class="container py-5"><div class="alert alert-danger">${esc(e.message)}</div></div>`;
        throw e;
    }
    if (estado.requiereConfiguracionInicial) {
        location.replace('/configuracion-inicial.html');
        throw new Error('Configuración inicial pendiente');
    }
    if (!estado.autenticado) {
        location.replace('/login.html?volver=' + encodeURIComponent(location.pathname + location.search));
        throw new Error('Sin sesión');
    }
    const usuario = estado.usuario;
    const negocio = estado.negocio;
    const tiene = p => usuario.permisos.includes(p);
    const requeridos = permiso ? [].concat(permiso) : [];
    const permitido = !requeridos.length || requeridos.some(tiene);

    armarMarco(titulo, usuario, negocio);
    document.title = (titulo ? titulo + ' · ' : '') + negocio.nombre;

    if (!permitido) {
        document.querySelector('.contenido').innerHTML = `<div class="tarjeta"><div class="tarjeta-cuerpo">
            <h2 class="h5"><i class="bi bi-lock"></i> Sin acceso</h2>
            <p class="mb-2">Su cargo (${esc(usuario.rolNombre)}) no tiene permiso para usar esta sección.</p>
            <a class="btn btn-primary" href="${paginaInicial(usuario.permisos)}">Ir a mi página de inicio</a></div></div>`;
        throw new Error('Sin permiso');
    }
    return { usuario, negocio, tiene };
}

function armarMarco(titulo, usuario, negocio) {
    const vista = document.getElementById('vista');
    const actual = location.pathname;
    const enlaces = MENU.map(s => {
        const visibles = s.items.filter(i => usuario.permisos.includes(i.permiso));
        if (!visibles.length) return '';
        return (s.seccion ? `<div class="seccion">${esc(s.seccion)}</div>` : '') + visibles.map(i =>
            `<a class="enlace ${actual === i.href ? 'activo' : ''}" href="${i.href}" ${actual === i.href ? 'aria-current="page"' : ''}>
                <i class="bi bi-${i.icono}" aria-hidden="true"></i><span>${esc(i.texto)}</span></a>`).join('');
    }).join('');
    const logo = negocio.logoUrl
        ? `<img src="${esc(negocio.logoUrl)}" alt="">`
        : `<span class="icono-marca" aria-hidden="true"><i class="bi bi-shop"></i></span>`;

    const app = document.createElement('div');
    app.className = 'app';
    app.innerHTML = `
        <nav class="barra-lateral" id="barra-lateral" aria-label="Menú principal">
            <a class="marca" href="/inicio.html">${logo}<span>${esc(negocio.nombre)}</span></a>
            ${enlaces}
            <div class="seccion">Mi cuenta</div>
            <a class="enlace ${actual === '/perfil.html' ? 'activo' : ''}" href="/perfil.html"><i class="bi bi-person-circle" aria-hidden="true"></i><span>Mi perfil</span></a>
            <a class="enlace" href="#" id="enlace-salir"><i class="bi bi-box-arrow-right" aria-hidden="true"></i><span>Cerrar sesión</span></a>
            <div class="p-3"></div>
        </nav>
        <div class="fondo-oscuro" id="fondo-oscuro"></div>
        <div class="principal">
            ${negocio.datosDemo ? `<div class="aviso-demo"><i class="bi bi-exclamation-triangle"></i>
                Este sistema tiene <strong>datos de demostración</strong>. No son datos reales del negocio.</div>` : ''}
            <header class="encabezado">
                <button class="btn btn-outline-secondary btn-sm btn-menu" id="btn-menu" type="button" aria-label="Abrir menú" aria-controls="barra-lateral">
                    <i class="bi bi-list"></i></button>
                <div><h1>${esc(titulo || '')}</h1></div>
                <div class="usuario d-flex align-items-center gap-2">
                    <div class="text-end lh-sm d-none d-sm-block">
                        <div class="fw-semibold">${esc(usuario.nombre)}</div>
                        <div class="negocio">${esc(usuario.rolNombre)}</div>
                    </div>
                    <i class="bi bi-person-circle fs-4 text-secondary" aria-hidden="true"></i>
                </div>
            </header>
            <main class="contenido" id="contenido"></main>
        </div>`;
    document.body.prepend(app);
    const contenido = app.querySelector('#contenido');
    if (vista) {
        while (vista.firstChild) contenido.appendChild(vista.firstChild);
        vista.remove();
    }

    const barra = app.querySelector('#barra-lateral');
    const fondo = app.querySelector('#fondo-oscuro');
    const alternar = abrir => {
        barra.classList.toggle('abierta', abrir);
        fondo.classList.toggle('visible', abrir);
    };
    app.querySelector('#btn-menu').addEventListener('click', () => alternar(!barra.classList.contains('abierta')));
    fondo.addEventListener('click', () => alternar(false));
    app.querySelector('#enlace-salir').addEventListener('click', async ev => {
        ev.preventDefault();
        try {
            await api('/api/sesion/salir', { metodo: 'POST' });
        } catch (e) {
            aviso('No se pudo cerrar la sesión en el servidor: ' + e.message, 'alerta');
        }
        location.href = '/login.html';
    });
}
