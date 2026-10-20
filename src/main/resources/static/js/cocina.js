// Panel de cocina: pedidos activos por estado, con observaciones visibles y tiempo de espera.
import { api } from './comun/api.js';
import { esc, nombre, hora, minutosDesde, vacio, ocupado, aviso } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';
import { cambiarEstado, TEXTO_ACCION, ICONO_ESTADO, verPedido } from './comun/pedido.js';
import { escucharPedidos, sonar } from './comun/eventos.js';

const { tiene } = await iniciarPagina({ titulo: 'Panel de cocina', permiso: 'pedidos.ver' });
const $ = id => document.getElementById(id);
const ESTADOS = ['PENDIENTE', 'EN_PREPARACION', 'LISTO'];
let pedidos = [];
let conocidos = null; // ids ya vistos, para resaltar los nuevos
let nuevos = new Set();

try { $('sonido').checked = localStorage.getItem('cocina.sonido') !== 'no'; } catch (e) { /* sin almacenamiento */ }
$('sonido').addEventListener('change', () => {
    try { localStorage.setItem('cocina.sonido', $('sonido').checked ? 'si' : 'no'); } catch (e) { /* sin almacenamiento */ }
});

function ticket(p) {
    const min = minutosDesde(p.creadoEn);
    const lugar = p.tipo === 'LOCAL' ? (p.mesa || 'Local') : nombre(p.tipo);
    const acciones = tiene('pedidos.estado') ? p.siguientesEstados.filter(e => e !== 'CANCELADO' && e !== 'PENDIENTE')
        .map(e => `<button type="button" class="btn btn-sm ${e === 'EN_PREPARACION' && p.estado === 'LISTO' ? 'btn-outline-secondary' : 'btn-primary'}" data-id="${p.id}" data-estado="${e}">
            <i class="bi bi-${ICONO_ESTADO[e]}"></i> ${esc(e === 'EN_PREPARACION' && p.estado === 'LISTO' ? 'Regresar a preparación' : TEXTO_ACCION[e])}</button>`).join('') : '';
    return `<article class="ticket-cocina ${nuevos.has(p.id) ? 'nuevo' : ''}">
        <div class="cabecera"><span class="numero">${esc(p.numero)}</span>
            <span class="${min >= 20 ? 'tiempo-alto' : 'text-secondary'}" title="Hora de registro ${hora(p.creadoEn)}"><i class="bi bi-clock"></i> ${min} min</span></div>
        <div class="px-3 pt-2 small"><strong>${esc(lugar)}</strong>${p.clienteNombre ? ' · ' + esc(p.clienteNombre) : ''}</div>
        <ul>${p.detalles.map(d => `<li><strong>${d.cantidad} ×</strong> ${esc(d.nombreProducto)}
            ${d.complementos.length ? `<div class="small">+ ${d.complementos.map(c => esc(c.nombre)).join(', ')}</div>` : ''}
            ${d.observaciones ? `<div class="obs"><i class="bi bi-exclamation-circle"></i> ${esc(d.observaciones)}</div>` : ''}</li>`).join('')}</ul>
        ${p.notas ? `<div class="px-3 pb-2 obs small"><i class="bi bi-sticky"></i> ${esc(p.notas)}</div>` : ''}
        <div class="acciones">${acciones}<button type="button" class="btn btn-sm btn-link ms-auto" data-ver="${p.id}">Detalle</button></div>
    </article>`;
}

function dibujar() {
    ESTADOS.forEach(e => {
        const lista = pedidos.filter(p => p.estado === e);
        $('n-' + e).textContent = lista.length;
        $('col-' + e).innerHTML = lista.length ? lista.map(ticket).join('') : `<div class="tarjeta">${vacio('Sin pedidos', 'check2')}</div>`;
    });
}

async function cargar() {
    try {
        pedidos = await api('/api/pedidos/cocina');
    } catch (e) {
        aviso(e.message, 'error');
        return;
    }
    const ids = new Set(pedidos.map(p => p.id));
    if (conocidos) {
        nuevos = new Set([...ids].filter(id => !conocidos.has(id)));
        if (nuevos.size && $('sonido').checked) sonar();
    }
    conocidos = ids;
    dibujar();
}

document.querySelector('.tablero-cocina').addEventListener('click', ev => {
    const b = ev.target.closest('[data-estado]');
    if (b) {
        const p = pedidos.find(x => x.id === Number(b.dataset.id));
        ocupado(b, async () => { if (await cambiarEstado(p, b.dataset.estado)) cargar(); });
        return;
    }
    const v = ev.target.closest('[data-ver]');
    if (v) verPedido(Number(v.dataset.ver), { tiene, alCambiar: cargar }).catch(e => aviso(e.message, 'error'));
});

let pendiente = null;
escucharPedidos(() => { clearTimeout(pendiente); pendiente = setTimeout(cargar, 250); }, $('conexion'));
setInterval(dibujar, 30000); // actualiza los minutos de espera
cargar();
