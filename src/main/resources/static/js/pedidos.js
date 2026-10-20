// Lista de pedidos con filtros, paginacion, detalle y actualizacion en tiempo real.
import { api, consulta } from './comun/api.js';
import { esc, dinero, fechaHora, nombre, estado, cargando, vacio, paginacion, parametro, aviso } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';
import { verPedido } from './comun/pedido.js';
import { escucharPedidos } from './comun/eventos.js';

const { tiene } = await iniciarPagina({ titulo: 'Pedidos', permiso: 'pedidos.ver' });
const $ = id => document.getElementById(id);
const form = $('filtros');
let pagina = 0;

if (!tiene('pedidos.crear')) $('btn-nuevo').classList.add('d-none');

function filtros() {
    return {
        numero: form.numero.value.trim(), estado: form.estado.value, tipo: form.tipo.value,
        desde: form.desde.value, hasta: form.hasta.value, pagina, tamano: 20
    };
}

async function resumen() {
    const activos = await api('/api/pedidos/cocina');
    const cuenta = e => activos.filter(p => p.estado === e).length;
    const tarjetas = [['PENDIENTE', 'hourglass-split', 'Pendientes'], ['EN_PREPARACION', 'fire', 'En preparación'], ['LISTO', 'check2-circle', 'Listos para entregar']];
    $('resumen').innerHTML = tarjetas.map(([e, i, t]) => `<div class="col-4"><button type="button" class="tarjeta indicador w-100 text-start" data-estado="${e}">
        <div class="etiqueta"><i class="bi bi-${i}"></i> ${t}</div><div class="valor">${cuenta(e)}</div></button></div>`).join('');
}
$('resumen').addEventListener('click', ev => {
    const b = ev.target.closest('[data-estado]');
    if (!b) return;
    form.estado.value = b.dataset.estado;
    pagina = 0;
    cargar();
});

async function cargar() {
    const cuerpo = $('filas');
    if (!cuerpo.children.length) cuerpo.innerHTML = `<tr><td colspan="8"><div class="cargando">Cargando...</div></td></tr>`;
    try {
        const r = await api('/api/pedidos' + consulta(filtros()));
        cuerpo.innerHTML = r.contenido.length ? r.contenido.map(p => `<tr>
            <td class="fw-semibold">${esc(p.numero)}</td>
            <td class="text-nowrap">${fechaHora(p.creadoEn)}</td>
            <td>${esc(nombre(p.tipo))}</td>
            <td>${esc(p.mesa || p.clienteNombre || '')}</td>
            <td class="num">${dinero(p.total)}</td>
            <td>${estado(p.estado)}</td>
            <td>${p.venta ? `${esc(p.venta.numero)} ${estado(p.venta.estado)}` : '<span class="text-secondary small">Sin cobrar</span>'}</td>
            <td class="text-end"><button class="btn btn-sm btn-outline-primary" data-id="${p.id}">Ver</button></td></tr>`).join('')
            : `<tr><td colspan="8">${vacio('No hay pedidos con estos filtros', 'receipt')}</td></tr>`;
        paginacion($('pie'), r, p => { pagina = p; cargar(); });
    } catch (e) {
        cuerpo.innerHTML = `<tr><td colspan="8"><div class="alert alert-danger m-2">${esc(e.message)}</div></td></tr>`;
    }
}

function refrescar() {
    cargar();
    resumen().catch(() => {});
}

$('filas').addEventListener('click', ev => {
    const b = ev.target.closest('[data-id]');
    if (b) verPedido(Number(b.dataset.id), { tiene, alCambiar: refrescar }).catch(e => aviso(e.message, 'error'));
});
form.addEventListener('submit', ev => { ev.preventDefault(); pagina = 0; cargar(); });
form.addEventListener('reset', () => setTimeout(() => { pagina = 0; cargar(); }));

refrescar();
let pendiente = null;
escucharPedidos(() => {
    // Agrupa varias notificaciones seguidas en una sola recarga
    clearTimeout(pendiente);
    pendiente = setTimeout(refrescar, 300);
}, $('conexion'));

const abrir = parametro('id');
if (abrir) verPedido(Number(abrir), { tiene, alCambiar: refrescar }).catch(e => aviso(e.message, 'error'));
