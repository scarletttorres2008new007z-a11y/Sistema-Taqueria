// Ticket de prueba para verificar la impresora termica (no usa ni crea datos reales).
import { api } from './comun/api.js';
import { esc } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

await iniciarPagina({ titulo: 'Prueba de impresión', permiso: ['configuracion.gestionar', 'comprobantes.imprimir'] });
const $ = id => document.getElementById(id);
const conf = await api('/api/configuracion');

function dibujar() {
    const ancho = Number($('ancho').value);
    const columnas = ancho === 58 ? 32 : 42;
    const regla = '1234567890'.repeat(5).slice(0, columnas);
    $('ticket').className = 'papel-termico ancho-' + ancho;
    $('ticket').innerHTML = `
        ${conf.logoUrl ? `<img class="logo-ticket" src="${esc(conf.logoUrl)}" alt="">` : ''}
        <div class="centro negrita grande">${esc(conf.nombreComercial)}</div>
        <hr><div class="centro negrita">PÁGINA DE PRUEBA DE IMPRESIÓN</div>
        <div class="centro">No es un comprobante. No tiene valor.</div><hr>
        <div>Papel configurado: ${ancho} mm</div>
        <div>Fecha: ${new Date().toLocaleString('es-SV', { timeZone: 'America/El_Salvador' })}</div>
        <div style="white-space:pre">${regla}</div>
        <div style="white-space:pre">${'-'.repeat(columnas)}</div>
        <table><tr><td>Texto a la izquierda</td><td class="der">$123.45</td></tr>
        <tr class="negrita grande"><td>TOTAL</td><td class="der">$9,999.99</td></tr></table>
        <hr><div class="centro">Acentos: á é í ó ú ñ Ñ ¿? ¡!</div>
        <div class="leyenda">Si puede leer esta línea completa, el ancho es correcto.</div>
        <div class="centro" style="margin-top:2mm">.</div>`;
    const alto = Math.ceil($('ticket').scrollHeight * 25.4 / 96) + 6;
    let estilo = document.getElementById('estilo-pagina');
    if (!estilo) { estilo = document.createElement('style'); estilo.id = 'estilo-pagina'; document.head.appendChild(estilo); }
    estilo.textContent = `@page { size: ${ancho}mm ${alto}mm; margin: 0; } @media print { html, body { width: ${ancho}mm; margin: 0 !important; } }`;
}
$('ancho').value = String(conf.anchoPapelMm);
$('ancho').addEventListener('change', dibujar);
$('imprimir').addEventListener('click', () => window.print());
dibujar();
