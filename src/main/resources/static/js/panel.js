// Panel principal: indicadores del periodo, actividad de hoy, graficos y alertas.
import { api, consulta } from './comun/api.js';
import { esc, dinero, numero, fecha, hoyISO, aviso, vacio } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Panel principal', permiso: 'dashboard.ver' });
const $ = id => document.getElementById(id);
const form = $('filtros');
const graficos = {};
const COLOR = '#c2410c';
const TINTA = '#6b625a';
const REJILLA = 'rgba(31,27,22,.08)';

function rango(tipo) {
    const hoy = hoyISO();
    if (tipo === 'hoy') return [hoy, hoy];
    if (tipo === 'ayer') return [hoyISO(-1), hoyISO(-1)];
    if (tipo === 'semana') return [hoyISO(-6), hoy];
    return [hoy.slice(0, 8) + '01', hoy];
}

document.querySelectorAll('[data-rango]').forEach(b => b.addEventListener('click', () => {
    [form.desde.value, form.hasta.value] = rango(b.dataset.rango);
    document.querySelectorAll('[data-rango]').forEach(x => x.classList.toggle('active', x === b));
    cargar();
}));
form.addEventListener('submit', ev => {
    ev.preventDefault();
    document.querySelectorAll('[data-rango]').forEach(x => x.classList.remove('active'));
    cargar();
});

const tarjeta = (icono, etiqueta, valor, { detalle = '', clase = '', ayuda = '', col = 'col-6 col-md-4 col-xl-3', enlace = '' } = {}) =>
    `<div class="${col}"><${enlace ? `a href="${enlace}"` : 'div'} class="tarjeta indicador d-block text-reset text-decoration-none ${clase}" ${ayuda ? `title="${esc(ayuda)}"` : ''}>
        <div class="etiqueta"><i class="bi bi-${icono}"></i> ${esc(etiqueta)}</div>
        <div class="valor">${valor}</div>${detalle ? `<div class="detalle">${detalle}</div>` : ''}</${enlace ? 'a' : 'div'}></div>`;

async function cargar() {
    let d;
    try {
        d = await api('/api/dashboard' + consulta({ desde: form.desde.value, hasta: form.hasta.value }));
    } catch (e) {
        aviso(e.message, 'error');
        return;
    }
    $('periodo').textContent = d.desde === d.hasta ? 'Período: ' + fecha(d.desde) : `Período: ${fecha(d.desde)} al ${fecha(d.hasta)}`;
    const v = d.ventas;
    const h = d.hoy;

    $('hoy').innerHTML =
        tarjeta('calendar-check', 'Ventas de hoy', dinero(h.ventas_hoy), { clase: 'destacado', detalle: `${h.pedidos_hoy} pedido(s) hoy` }) +
        tarjeta('hourglass-split', 'Pedidos pendientes', h.pendientes, { enlace: '/cocina.html' }) +
        tarjeta('fire', 'En preparación', h.en_preparacion, { enlace: '/cocina.html' }) +
        tarjeta('check2-circle', 'Listos para entregar', h.listos, { enlace: '/cocina.html' });

    const utilidad = Number(d.utilidadEstimada);
    $('indicadores').innerHTML =
        tarjeta('receipt', 'Ventas brutas', dinero(v.ventas_brutas), { detalle: `${v.cantidad} venta(s) · ${d.pedidosPeriodo} pedido(s)`, ayuda: 'Productos vendidos antes de descuentos, IVA incluido' }) +
        tarjeta('tag', 'Descuentos', dinero(v.descuentos)) +
        tarjeta('cash-stack', 'Ventas netas', dinero(v.ventas_netas), { clase: 'destacado', detalle: `IVA incluido: ${dinero(v.iva)}`, ayuda: 'Ventas brutas menos descuentos' }) +
        tarjeta('wallet2', 'Ingresos cobrados', dinero(d.ingresosCobrados), { ayuda: 'Pagos recibidos en el período' }) +
        tarjeta('percent', 'Ventas sin IVA', dinero(v.ventas_sin_iva)) +
        tarjeta('basket', 'Costo estimado', dinero(v.costos), { ayuda: 'Según recetas al momento de vender' }) +
        tarjeta('lightning-charge', 'Gastos de operación', dinero(d.gastos), { enlace: tiene('gastos.ver') ? '/gastos.html' : '' }) +
        tarjeta('graph-up-arrow', 'Utilidad estimada', dinero(utilidad), { clase: utilidad >= 0 ? 'positivo' : 'negativo',
            detalle: `Utilidad bruta: ${dinero(d.utilidadBruta)}`, ayuda: 'Ventas sin IVA − costo estimado − gastos' });

    dibujarGraficos(d);
    alertas(d);
}

function dibujarGraficos(d) {
    if (!window.Chart) return;
    Chart.defaults.color = TINTA;
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    const base = (etiquetas, datos, opciones) => ({
        type: 'bar',
        data: { labels: etiquetas, datasets: [{ data: datos, backgroundColor: COLOR, hoverBackgroundColor: '#9a3412', borderRadius: 4, borderSkipped: 'start', maxBarThickness: 36 }] },
        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, ...opciones }
    });
    const dias = d.ventasPorDia;
    graficos.dias?.destroy();
    graficos.productos?.destroy();
    // Sin datos no se dibuja un eje vacio: se muestra un mensaje
    for (const [id, hay] of [['grafico-dias', dias.length], ['grafico-productos', d.topProductos.length]]) {
        const zona = $(id).parentElement;
        zona.querySelector('.vacio')?.remove();
        $(id).hidden = !hay;
        if (!hay) zona.insertAdjacentHTML('beforeend', vacio('Sin ventas en el período', 'bar-chart'));
    }
    graficos.dias = new Chart($('grafico-dias'), base(dias.map(x => fecha(x.dia).slice(0, 5)), dias.map(x => Number(x.total)), {
        scales: { y: { beginAtZero: true, grid: { color: REJILLA }, border: { display: false }, ticks: { callback: x => '$' + x } }, x: { grid: { display: false } } },
        plugins: { legend: { display: false }, tooltip: { callbacks: { title: i => fecha(dias[i[0].dataIndex].dia), label: i => `${dinero(i.raw)} · ${dias[i.dataIndex].cantidad} venta(s)` } } }
    }));
    const top = d.topProductos;
    graficos.productos = new Chart($('grafico-productos'), base(top.map(x => x.nombre), top.map(x => Number(x.cantidad)), {
        indexAxis: 'y',
        scales: { x: { beginAtZero: true, grid: { color: REJILLA }, border: { display: false }, ticks: { precision: 0 } }, y: { grid: { display: false } } },
        plugins: { legend: { display: false }, tooltip: { callbacks: { label: i => `${numero(i.raw)} unidad(es) · ${dinero(top[i.dataIndex].total)}` } } }
    }));
}

function alertas(d) {
    const bloque = (titulo, icono, contenido, enlace) => `<div class="col-md-6 col-xl-3"><section class="tarjeta h-100">
        <div class="tarjeta-titulo"><i class="bi bi-${icono}"></i> ${titulo}${enlace ? `<a class="ms-auto small" href="${enlace}">Ver</a>` : ''}</div>
        <div class="tarjeta-cuerpo small">${contenido}</div></section></div>`;
    const lista = (items, detalle) => items.length ? `<ul class="list-unstyled mb-0">${items.slice(0, 6).map(i => `<li class="d-flex justify-content-between gap-2 mb-1">
        <span>${esc(i.nombre)}</span><span class="text-nowrap ${i.estado === 'Agotado' || i.estado === 'Vencido' ? 'text-danger fw-semibold' : ''}">${detalle(i)}</span></li>`).join('')}
        ${items.length > 6 ? `<li class="text-secondary">y ${items.length - 6} más</li>` : ''}</ul>` : '<span class="text-success"><i class="bi bi-check2-circle"></i> Sin alertas</span>';
    const c = d.caja;
    $('alertas').innerHTML =
        bloque('Caja', 'safe', c.abierta ? `<div class="text-success fw-semibold mb-1"><i class="bi bi-unlock"></i> Abierta</div>
            <div>Efectivo esperado: <strong>${dinero(c.efectivoEsperado)}</strong></div><div>Tarjeta: ${dinero(c.ventasTarjeta)} · Transferencia: ${dinero(c.ventasTransferencia)}</div>
            <div class="text-secondary">Abrió ${esc(c.abiertaPor)}</div>` : '<div class="text-danger fw-semibold"><i class="bi bi-lock"></i> Cerrada</div><div class="text-secondary">Ábrala para poder cobrar.</div>', tiene('caja.ver') ? '/caja.html' : '') +
        bloque('Existencias bajas', 'exclamation-triangle', lista(d.existenciasBajas, i => `${numero(i.existencia)} ${esc(i.unidad)}`), tiene('inventario.ver') ? '/inventario.html' : '') +
        bloque('Vencimientos', 'calendar-event', lista(d.porVencer, i => `${esc(i.estado)} ${fecha(i.fechaVencimiento)}`), tiene('inventario.ver') ? '/inventario.html' : '') +
        bloque('Por cobrar y por pagar', 'cash', `<div class="mb-2">Ventas pendientes de pago: <strong>${d.ventasPendientes.cantidad}</strong> · ${dinero(d.ventasPendientes.saldo)}</div>
            <div>Deudas con proveedores: <strong>${dinero(d.deudasProveedores.saldo)}</strong> (${d.deudasProveedores.proveedores} proveedor(es))
            ${d.deudasProveedores.vencidas ? `<div class="text-danger">${d.deudasProveedores.vencidas} compra(s) con pago vencido</div>` : ''}</div>`, tiene('compras.ver') ? '/compras.html' : '');
}

[form.desde.value, form.hasta.value] = rango('hoy');
document.querySelector('[data-rango=hoy]').classList.add('active');
cargar();
