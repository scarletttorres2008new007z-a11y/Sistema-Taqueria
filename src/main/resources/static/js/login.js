// Inicio de sesion. Usa el formulario de Spring Security con el token CSRF de la cookie.
import { api, ErrorApi } from './comun/api.js';
import { esc } from './comun/ui.js';
import { paginaInicial } from './comun/layout.js';

const form = document.getElementById('form-acceso');
const error = document.getElementById('error');
const boton = document.getElementById('btn-entrar');

function leerCookie(nombre) {
    const par = document.cookie.split('; ').find(c => c.startsWith(nombre + '='));
    return par ? decodeURIComponent(par.split('=')[1]) : '';
}

function destino(permisos) {
    const volver = new URLSearchParams(location.search).get('volver');
    // Solo se aceptan rutas internas para evitar redirecciones a otros sitios
    if (volver && volver.startsWith('/') && !volver.startsWith('//') && !volver.startsWith('/login')) return volver;
    return paginaInicial(permisos);
}

async function iniciar() {
    const estado = await api('/api/sesion/estado');
    if (estado.requiereConfiguracionInicial) { location.replace('/configuracion-inicial.html'); return; }
    if (estado.autenticado) { location.replace(destino(estado.usuario.permisos)); return; }
    document.getElementById('negocio').textContent = estado.negocio.nombre;
    document.title = 'Iniciar sesión · ' + estado.negocio.nombre;
    if (estado.negocio.logoUrl) document.getElementById('logo').innerHTML = `<img src="${esc(estado.negocio.logoUrl)}" alt="">`;
    document.getElementById('aviso-demo').classList.toggle('d-none', !estado.negocio.datosDemo);
}

document.getElementById('ver-clave').addEventListener('click', () => {
    const c = document.getElementById('clave');
    c.type = c.type === 'password' ? 'text' : 'password';
});

form.addEventListener('submit', async ev => {
    ev.preventDefault();
    error.classList.add('d-none');
    const usuario = form.username.value.trim();
    const clave = form.password.value;
    if (!usuario || !clave) {
        error.textContent = 'Escriba su usuario y contraseña.';
        error.classList.remove('d-none');
        return;
    }
    boton.disabled = true;
    try {
        const r = await fetch('/api/sesion/entrar', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': leerCookie('XSRF-TOKEN'), 'Accept': 'application/json' },
            body: new URLSearchParams({ username: usuario, password: clave })
        });
        const datos = await r.json().catch(() => ({}));
        if (!r.ok) throw new ErrorApi(datos.mensaje || 'No se pudo iniciar sesión', r.status);
        location.replace(destino(datos.permisos || []));
    } catch (e) {
        error.textContent = e.estado === 0 || !e.estado ? 'No hay conexión con el servidor.' : e.message;
        if (e.estado === 403) error.textContent = 'La página caducó. Recárguela e intente de nuevo.';
        error.classList.remove('d-none');
        form.password.value = '';
        form.password.focus();
    } finally {
        boton.disabled = false;
    }
});

iniciar().catch(e => {
    error.textContent = e.message;
    error.classList.remove('d-none');
});
