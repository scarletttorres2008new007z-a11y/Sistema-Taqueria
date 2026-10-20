// Envia a cada usuario a la primera seccion que su cargo puede usar.
import { api } from './comun/api.js';
import { paginaInicial } from './comun/layout.js';

api('/api/sesion/estado').then(e => {
    if (e.requiereConfiguracionInicial) location.replace('/configuracion-inicial.html');
    else if (!e.autenticado) location.replace('/login.html');
    else location.replace(paginaInicial(e.usuario.permisos));
}).catch(() => location.replace('/login.html'));
