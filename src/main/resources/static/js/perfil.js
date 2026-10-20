import { api } from './comun/api.js';
import { esc, aviso, ocupado } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { usuario } = await iniciarPagina({ titulo: 'Mi perfil' });

document.getElementById('datos').innerHTML = `<dl class="row mb-0">
    <dt class="col-5">Nombre</dt><dd class="col-7">${esc(usuario.nombre)}</dd>
    <dt class="col-5">Usuario</dt><dd class="col-7">${esc(usuario.username)}</dd>
    <dt class="col-5">Cargo</dt><dd class="col-7">${esc(usuario.rolNombre)}</dd>
    <dt class="col-5">Permisos</dt><dd class="col-7">${usuario.permisos.length}</dd></dl>`;

const form = document.getElementById('form-clave');
form.addEventListener('submit', ev => {
    ev.preventDefault();
    ocupado(form.querySelector('button[type=submit]'), async () => {
        if (form.nueva.value.length < 8) throw new Error('La contraseña nueva debe tener al menos 8 caracteres');
        if (form.nueva.value !== document.getElementById('nueva2').value) throw new Error('Las contraseñas nuevas no coinciden');
        await api('/api/sesion/password', { datos: { actual: form.actual.value, nueva: form.nueva.value } });
        form.reset();
        aviso('Contraseña actualizada');
    });
});
