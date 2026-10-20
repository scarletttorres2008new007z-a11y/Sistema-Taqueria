// Vista e impresion del comprobante interno en formato termico (58 u 80 mm).
import { api } from './comun/api.js';
import { esc, fechaHora, estado, aviso, parametro, ocupado } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';
import { htmlTicket, pdfTicket } from './comun/ticket.js';

const { tiene } = await iniciarPagina({ titulo: 'Comprobante', permiso: 'comprobantes.ver' });
const $ = id => document.getElementById(id);
const id = parametro('id');
const ventaId = parametro('venta');
let vista;

function anchoGuardado() {
    try { return localStorage.getItem('papel.ancho'); } catch (e) { return null; }
}

/** Ajusta la hoja de impresion al ancho del papel y al largo real del ticket. */
function ajustarPagina() {
    const ancho = Number($('ancho').value);
    const ticket = $('ticket');
    ticket.className = 'papel-termico ancho-' + ancho;
    const altoMm = Math.ceil(ticket.scrollHeight * 25.4 / 96) + 6;
    let estilo = document.getElementById('estilo-pagina');
    if (!estilo) {
        estilo = document.createElement('style');
        estilo.id = 'estilo-pagina';
        document.head.appendChild(estilo);
    }
    estilo.textContent = `@page { size: ${ancho}mm ${altoMm}mm; margin: 0; }
        @media print { html, body { width: ${ancho}mm; margin: 0 !important; } .papel-termico { width: ${ancho}mm !important; } }`;
}

function dibujar(copia = 0) {
    $('ticket').innerHTML = htmlTicket(vista, { copia });
    const c = vista.comprobante;
    $('estado-comprobante').innerHTML = `<div class="d-flex justify-content-between"><strong>${esc(c.numero)}</strong>${estado(c.estado)}</div>
        <div class="small text-secondary">Emitido ${fechaHora(c.fechaEmision)} · Impresiones: <span id="n-impresiones">${c.impresiones}</span>
        ${c.ultimaImpresion ? '· Última: ' + fechaHora(c.ultimaImpresion) : ''}</div>`;
    const img = $('ticket').querySelector('img');
    if (img && !img.complete) img.addEventListener('load', ajustarPagina, { once: true });
    ajustarPagina();
}

async function cargar() {
    vista = await api(id ? '/api/comprobantes/' + id : '/api/comprobantes/venta/' + ventaId);
    $('ancho').value = anchoGuardado() || String(vista.anchoPapelMm);
    $('ver-venta').href = '/ventas.html?id=' + vista.venta.id;
    if (!tiene('ventas.ver')) $('ver-venta').classList.add('d-none');
    if (!tiene('comprobantes.imprimir')) $('imprimir').classList.add('d-none');
    document.title = 'Comprobante ' + vista.comprobante.numero;
    dibujar();
    if (parametro('imprimir') === '1' && tiene('comprobantes.imprimir') && vista.comprobante.impresiones === 0) {
        setTimeout(() => $('imprimir').click(), 300);
    }
}

$('ancho').addEventListener('change', () => {
    try { localStorage.setItem('papel.ancho', $('ancho').value); } catch (e) { /* sin almacenamiento */ }
    ajustarPagina();
});

$('imprimir').addEventListener('click', ev => ocupado(ev.currentTarget, async () => {
    // Se registra antes de abrir el cuadro de impresion; si ya se imprimio, sale marcado como copia
    const r = await api(`/api/comprobantes/${vista.comprobante.id}/impresiones`, { metodo: 'POST' });
    vista.comprobante.impresiones = r.impresiones;
    vista.comprobante.ultimaImpresion = new Date().toISOString();
    dibujar(r.impresiones > 1 ? r.impresiones - 1 : 0);
    await new Promise(res => setTimeout(res, 50));
    window.print();
}));

$('pdf').addEventListener('click', () => {
    if (!window.jspdf) { aviso('No se pudo cargar el generador de PDF', 'error'); return; }
    pdfTicket(vista, Number($('ancho').value));
});

cargar().catch(e => { $('ticket').innerHTML = `<div class="alert alert-danger">${esc(e.message)}</div>`; });
