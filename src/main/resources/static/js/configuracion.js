// Configuracion del negocio, logo, impresora y estado de la facturacion electronica.
import { api } from './comun/api.js';
import { esc, aviso, leerFormulario, llenarFormulario, marcarErrores, ocupado, confirmar } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

await iniciarPagina({ titulo: 'Configuración', permiso: 'configuracion.gestionar' });
const $ = id => document.getElementById(id);
const form = $('form-negocio');

function mostrarLogo(url) {
    $('logo-actual').innerHTML = url ? `<img src="${esc(url)}" alt="Logo actual" style="max-width:90px;max-height:90px;object-fit:contain">` : '<span class="small text-secondary">Sin logo</span>';
    $('quitar-logo').disabled = !url;
}

async function cargar() {
    const c = await api('/api/configuracion');
    llenarFormulario(form, c);
    form.anchoPapelMm.value = String(c.anchoPapelMm);
    mostrarLogo(c.logoUrl);
    $('aviso-datos').classList.toggle('d-none', !!c.nit || !!c.razonSocial);
    const fe = await api('/api/configuracion/facturacion-electronica');
    $('fe').innerHTML = `<div class="alert alert-warning py-2"><strong>No integrada.</strong> ${esc(fe.mensaje)}</div>
        <p class="mb-1">Leyenda que imprime cada comprobante interno:</p><p class="border rounded p-2 bg-light">${esc(fe.leyendaComprobante)}</p>
        <p class="mb-1">Para emitir DTE válidos hace falta:</p><ul class="ps-3 mb-0">${fe.faltantes.map(f => `<li>${esc(f)}</li>`).join('')}</ul>`;
}

form.addEventListener('submit', ev => {
    ev.preventDefault();
    ocupado(form.querySelector('[type=submit]'), async () => {
        try {
            await api('/api/configuracion', { metodo: 'PUT', datos: leerFormulario(form) });
            marcarErrores(form, {});
            aviso('Datos guardados. Los comprobantes nuevos usarán estos datos; los anteriores conservan los datos con los que se emitieron.');
            cargar();
        } catch (e) {
            marcarErrores(form, e.campos);
            throw e;
        }
    });
});

$('subir-logo').addEventListener('click', ev => ocupado(ev.currentTarget, async () => {
    const f = $('archivo-logo');
    if (!f.files.length) throw new Error('Seleccione una imagen');
    const fd = new FormData();
    fd.append('archivo', f.files[0]);
    const c = await api('/api/configuracion/logo', { formulario: fd });
    mostrarLogo(c.logoUrl);
    f.value = '';
    aviso('Logo actualizado');
}));
$('quitar-logo').addEventListener('click', async ev => {
    if (!await confirmar('¿Quitar el logo del sistema y de los comprobantes nuevos?', { textoBoton: 'Quitar' })) return;
    ocupado(ev.target.closest('button'), async () => { await api('/api/configuracion/logo', { metodo: 'DELETE' }); mostrarLogo(null); aviso('Logo quitado'); });
});

cargar().catch(e => aviso(e.message, 'error'));
