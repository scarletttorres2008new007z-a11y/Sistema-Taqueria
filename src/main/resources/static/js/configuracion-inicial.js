// Creacion del administrador inicial (solo mientras la base no tiene usuarios).
import { api } from './comun/api.js';
import { aviso, marcarErrores } from './comun/ui.js';

const form = document.getElementById('form');
const error = document.getElementById('error');

api('/api/sesion/estado').then(e => {
    if (!e.requiereConfiguracionInicial) location.replace('/login.html');
});

form.addEventListener('submit', async ev => {
    ev.preventDefault();
    error.classList.add('d-none');
    const datos = {
        codigoConfiguracion: form.codigoConfiguracion.value.trim(),
        nombre: form.nombre.value.trim(),
        username: form.username.value.trim(),
        password: form.password.value
    };
    let problema = null;
    if (!datos.codigoConfiguracion) problema = 'Escriba el código de configuración.';
    else if (!datos.nombre || !datos.username) problema = 'Complete el nombre y el usuario.';
    else if (!/^[A-Za-z0-9._-]{3,50}$/.test(datos.username)) problema = 'El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo.';
    else if (datos.password.length < 8) problema = 'La contraseña debe tener al menos 8 caracteres.';
    else if (datos.password !== document.getElementById('password2').value) problema = 'Las contraseñas no coinciden.';
    if (problema) {
        error.textContent = problema;
        error.classList.remove('d-none');
        return;
    }
    const btn = document.getElementById('btn');
    btn.disabled = true;
    try {
        await api('/api/configuracion-inicial/administrador', { datos });
        aviso('Administrador creado. Ahora inicie sesión.');
        setTimeout(() => location.replace('/login.html'), 1200);
    } catch (e) {
        marcarErrores(form, e.campos);
        error.textContent = e.message;
        error.classList.remove('d-none');
        btn.disabled = false;
    }
});
