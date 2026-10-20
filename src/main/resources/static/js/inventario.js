// Inventario: articulos, entradas, salidas, ajustes por conteo, historial de movimientos y alertas.
import { api, consulta } from './comun/api.js';
import { esc, dinero, numero, fecha, fechaHora, nombre, estado, aviso, formulario, campo, opciones, tablaLocal, vacio, paginacion, hoyISO } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Inventario', permiso: 'inventario.ver' });
const $ = id => document.getElementById(id);
const PERMISO_MOV = { ENTRADA: 'inventario.entradas', SALIDA: 'inventario.salidas', AJUSTE: 'inventario.ajustar' };
document.querySelectorAll('[data-mov]').forEach(b => { if (!tiene(PERMISO_MOV[b.dataset.mov])) b.classList.add('d-none'); });
if (!tiene('inventario.gestionar')) $('nuevo').classList.add('d-none');

let items = [], unidades = [], proveedores = [], nombres = {};
let tabla, paginaMov = 0;

async function cargar() {
    items = await api('/api/inventario');
    if (!unidades.length) unidades = await api('/api/unidades');
    if (tiene('inventario.gestionar') && !proveedores.length) proveedores = await api('/api/proveedores').catch(() => []);
    $('m-item').innerHTML = opciones(items, 'id', 'nombre', $('m-item').value, 'Todos');
    dibujar();
    indicadores();
    alertas();
}

function indicadores() {
    const activos = items.filter(i => i.activo);
    const valor = activos.reduce((s, i) => s + Number(i.existencia) * Number(i.costoUnitario), 0);
    const tarjeta = (icono, etiqueta, valorTxt, clase = '') => `<div class="col-6 col-lg-3"><div class="tarjeta indicador ${clase}">
        <div class="etiqueta"><i class="bi bi-${icono}"></i> ${etiqueta}</div><div class="valor">${valorTxt}</div></div></div>`;
    $('indicadores').innerHTML = tarjeta('box-seam', 'Artículos activos', activos.length) +
        tarjeta('currency-dollar', 'Valor del inventario (al costo)', dinero(valor), 'destacado') +
        tarjeta('exclamation-octagon', 'Agotados', activos.filter(i => i.estadoExistencia === 'AGOTADO').length, 'negativo') +
        tarjeta('exclamation-triangle', 'Bajo el mínimo', activos.filter(i => i.estadoExistencia === 'BAJO').length);
}

function filas() {
    const est = $('filtro-estado').value;
    return items.filter(i => ($('ver-inactivos').checked || i.activo) && (!est || i.estadoExistencia === est));
}

function dibujar() {
    const fila = i => `<tr class="${i.activo ? '' : 'text-secondary'}">
        <td class="text-nowrap">${esc(i.codigo)}</td><td><div class="fw-semibold">${esc(i.nombre)}</div><div class="small text-secondary">${esc(nombre(i.tipo))}${i.lote ? ' · Lote ' + esc(i.lote) : ''}${i.demo ? ' · <span class="badge text-bg-warning">Demo</span>' : ''}</div></td>
        <td>${esc(i.categoria)}</td>
        <td class="num fw-semibold">${numero(i.existencia)} ${esc(i.unidad.codigo)}</td><td class="num">${numero(i.existenciaMinima)}</td>
        <td class="num">${dinero(i.costoUnitario)}</td><td class="num">${dinero(Number(i.existencia) * Number(i.costoUnitario))}</td>
        <td class="small">${esc(i.proveedor ? i.proveedor.nombreComercial : '')}</td>
        <td class="small">${i.fechaVencimiento ? fecha(i.fechaVencimiento) + (i.estadoVencimiento ? '<br>' + estado(i.estadoVencimiento) : '') : ''}</td>
        <td>${i.activo ? estado(i.estadoExistencia) : estado('INACTIVO', 'Inactivo')}</td>
        <td class="text-end text-nowrap"><button class="btn btn-sm btn-outline-secondary" data-historial="${i.id}" title="Ver movimientos" aria-label="Ver movimientos"><i class="bi bi-clock-history"></i></button>
            ${tiene('inventario.gestionar') ? `<button class="btn btn-sm btn-outline-primary" data-editar="${i.id}">Editar</button>` : ''}</td></tr>`;
    const filtro = (i, t) => i.nombre.toLowerCase().includes(t) || i.codigo.toLowerCase().includes(t) || i.categoria.toLowerCase().includes(t);
    if (tabla) tabla.actualizar(filas());
    else tabla = tablaLocal({ cuerpo: $('filas'), pie: $('pie'), buscador: $('buscar'), filas: filas(), fila, filtro, porPagina: 25,
        vacioTexto: 'No hay artículos. Cree los ingredientes y materiales con "Nuevo artículo".' });
}
$('filtro-estado').addEventListener('change', dibujar);
$('ver-inactivos').addEventListener('change', dibujar);

function editarArticulo(i) {
    const categoriasUsadas = [...new Set(items.map(x => x.categoria))].sort();
    formulario({
        titulo: i ? 'Editar ' + i.nombre : 'Nuevo artículo de inventario', tamano: 'modal-lg',
        html: `<div class="row g-2">
            ${campo('nombre', 'Nombre', { col: 'col-md-8', extra: 'maxlength="120" required' })}
            ${campo('codigo', 'Código', { col: 'col-md-4', extra: 'maxlength="30"', ayuda: 'Vacío = automático' })}
            ${campo('tipo', 'Tipo', { col: 'col-md-4', opcionesHtml: '<option value="INGREDIENTE">Ingrediente</option><option value="PRODUCTO_TERMINADO">Producto terminado / reventa</option><option value="MATERIAL">Material (desechables, limpieza)</option>' })}
            ${campo('categoria', 'Categoría', { col: 'col-md-4', extra: 'maxlength="60" required list="lista-categorias"' })}
            ${campo('unidadId', 'Unidad de medida', { col: 'col-md-4', extra: 'data-numero required', opcionesHtml: opciones(unidades, 'id', u => `${u.nombre} (${u.codigo})`, null, 'Seleccione') })}
            ${i ? '' : campo('existenciaInicial', 'Existencia inicial', { tipo: 'number', col: 'col-md-4', extra: 'min="0" step="0.001"', ayuda: 'Se registra como movimiento de entrada' })}
            ${campo('existenciaMinima', 'Existencia mínima (alerta)', { tipo: 'number', col: 'col-md-4', extra: 'min="0" step="0.001" required' })}
            ${campo('costoUnitario', 'Costo por unidad ($)', { tipo: 'number', col: 'col-md-4', extra: 'min="0" step="0.0001" required' })}
            ${campo('proveedorId', 'Proveedor habitual', { col: 'col-md-6', extra: 'data-numero', opcionesHtml: opciones(proveedores.filter(p => p.activo), 'id', 'nombreComercial', null, 'Ninguno') })}
            ${campo('lote', 'Lote', { col: 'col-md-3', extra: 'maxlength="60"' })}
            ${campo('fechaVencimiento', 'Vencimiento', { tipo: 'date', col: 'col-md-3' })}
            ${campo('activo', 'Activo', { tipo: 'checkbox' })}
            <datalist id="lista-categorias">${categoriasUsadas.map(c => `<option value="${esc(c)}">`).join('')}</datalist>
            ${i ? `<div class="col-12 small text-secondary">La existencia no se edita aquí: use Entrada, Salida o Ajuste para que quede registrado el movimiento.
                Existencia actual: <strong>${numero(i.existencia)} ${esc(i.unidad.codigo)}</strong>.</div>` : ''}</div>`,
        valores: i ? { ...i, unidadId: i.unidad.id, proveedorId: i.proveedor ? i.proveedor.id : '' }
            : { tipo: 'INGREDIENTE', existenciaMinima: 0, costoUnitario: 0, activo: true },
        guardar: async (datos, f, clave) => {
            await api('/api/inventario' + (i ? '/' + i.id : ''), { metodo: i ? 'PUT' : 'POST', datos, clave });
            aviso('Artículo guardado');
            cargar();
        }
    });
}
$('nuevo').addEventListener('click', () => editarArticulo(null));

function movimiento(tipo, itemId) {
    const activos = items.filter(i => i.activo);
    if (!activos.length) { aviso('Primero cree artículos de inventario', 'alerta'); return; }
    const titulos = { ENTRADA: 'Registrar entrada', SALIDA: 'Registrar salida (merma, consumo interno, daño)', AJUSTE: 'Ajuste por conteo físico' };
    formulario({
        titulo: titulos[tipo], textoBoton: 'Registrar',
        html: `<div class="row g-2">
            ${campo('itemId', 'Artículo', { extra: 'data-numero required', opcionesHtml: opciones(activos, 'id', i => `${i.nombre} — ${numero(i.existencia)} ${i.unidad.codigo}`, itemId, 'Seleccione') })}
            ${campo('cantidad', tipo === 'AJUSTE' ? 'Existencia contada (cantidad real)' : 'Cantidad', { tipo: 'number', col: 'col-md-6', extra: 'min="0" step="0.001" required' })}
            ${tipo === 'ENTRADA' ? campo('costoUnitario', 'Costo por unidad (opcional)', { tipo: 'number', col: 'col-md-6', extra: 'min="0" step="0.0001"', ayuda: 'Actualiza el costo promedio' }) : '<div class="col-md-6"></div>'}
            ${campo('motivo', 'Motivo', { extra: 'maxlength="250" required', ayuda: tipo === 'AJUSTE' ? 'Ej.: conteo semanal' : tipo === 'SALIDA' ? 'Ej.: producto dañado, consumo del personal' : 'Ej.: compra en mercado, donación' })}
            ${tipo === 'ENTRADA' ? campo('lote', 'Lote (opcional)', { col: 'col-md-6', extra: 'maxlength="60"' }) + campo('fechaVencimiento', 'Vencimiento (opcional)', { tipo: 'date', col: 'col-md-6', extra: `min="${hoyISO()}"` }) : ''}
            <div class="col-12 small text-secondary" id="ayuda-mov"></div></div>`,
        alAbrir: form => {
            const ayuda = () => {
                const it = items.find(i => i.id === Number(form.itemId.value));
                const c = Number(form.cantidad.value);
                if (!it) { form.querySelector('#ayuda-mov').textContent = ''; return; }
                const actual = Number(it.existencia);
                const nueva = tipo === 'ENTRADA' ? actual + c : tipo === 'SALIDA' ? actual - c : c;
                form.querySelector('#ayuda-mov').innerHTML = form.cantidad.value === '' ? `Existencia actual: ${numero(actual)} ${esc(it.unidad.codigo)}`
                    : `Existencia: ${numero(actual)} → <strong class="${nueva < 0 ? 'text-danger' : ''}">${numero(nueva)} ${esc(it.unidad.codigo)}</strong>${nueva < 0 ? ' (no hay suficiente existencia)' : ''}`;
            };
            form.addEventListener('input', ayuda);
            form.addEventListener('change', ayuda);
            ayuda();
        },
        guardar: async (datos, f, clave) => {
            await api('/api/inventario/movimientos', { datos: { ...datos, tipo }, clave });
            aviso('Movimiento registrado');
            cargar();
            if ($('tab-movimientos').classList.contains('active')) cargarMovimientos();
        }
    });
}
document.querySelectorAll('[data-mov]').forEach(b => b.addEventListener('click', () => movimiento(b.dataset.mov)));

$('filas').addEventListener('click', ev => {
    const e = ev.target.closest('[data-editar]');
    const h = ev.target.closest('[data-historial]');
    if (e) editarArticulo(items.find(i => i.id === Number(e.dataset.editar)));
    if (h) {
        $('m-item').value = h.dataset.historial;
        paginaMov = 0;
        bootstrap.Tab.getOrCreateInstance($('pestana-movimientos')).show();
        cargarMovimientos();
    }
});

// ---------- Movimientos ----------

async function cargarMovimientos() {
    const f = $('filtros-mov');
    try {
        if (!Object.keys(nombres).length) nombres = await api('/api/usuarios/nombres').catch(() => ({}));
        const r = await api('/api/inventario/movimientos' + consulta({ itemId: f.itemId.value, tipo: f.tipo.value, desde: f.desde.value, hasta: f.hasta.value, pagina: paginaMov, tamano: 25 }));
        $('filas-mov').innerHTML = r.contenido.length ? r.contenido.map(m => `<tr>
            <td class="text-nowrap">${fechaHora(m.creadoEn)}</td><td>${esc(m.item.nombre)}</td><td>${esc(nombre(m.tipo))}</td>
            <td class="num ${Number(m.cantidad) < 0 ? 'text-danger' : 'text-success'}">${Number(m.cantidad) > 0 ? '+' : ''}${numero(m.cantidad)} ${esc(m.item.unidad.codigo)}</td>
            <td class="num">${numero(m.existenciaAnterior)}</td><td class="num">${numero(m.existenciaNueva)}</td>
            <td class="small">${esc(m.motivo || '')}${m.referenciaTipo ? `<div class="text-secondary">${esc(m.referenciaTipo)} #${m.referenciaId}</div>` : ''}</td>
            <td class="small">${esc(nombres[m.usuarioId] || '')}</td></tr>`).join('')
            : `<tr><td colspan="8">${vacio('No hay movimientos con estos filtros')}</td></tr>`;
        paginacion($('pie-mov'), r, p => { paginaMov = p; cargarMovimientos(); });
    } catch (e) {
        $('filas-mov').innerHTML = `<tr><td colspan="8"><div class="alert alert-danger m-2">${esc(e.message)}</div></td></tr>`;
    }
}
$('filtros-mov').addEventListener('submit', ev => { ev.preventDefault(); paginaMov = 0; cargarMovimientos(); });
$('pestana-movimientos').addEventListener('shown.bs.tab', cargarMovimientos);

// ---------- Alertas ----------

async function alertas() {
    const a = await api('/api/inventario/alertas');
    const total = a.agotados.length + a.bajos.length + a.vencidos.length + a.porVencer.length;
    $('n-alertas').textContent = total || '';
    const bloque = (titulo, icono, lista, detalle) => `<div class="col-md-6"><section class="tarjeta h-100">
        <div class="tarjeta-titulo"><i class="bi bi-${icono}"></i> ${titulo} <span class="badge text-bg-secondary ms-auto">${lista.length}</span></div>
        ${lista.length ? `<ul class="list-group list-group-flush">${lista.map(i => `<li class="list-group-item d-flex justify-content-between">
            <span>${esc(i.nombre)}</span><span class="small">${detalle(i)}</span></li>`).join('')}</ul>` : vacio('Nada que revisar', 'check2-circle')}</section></div>`;
    $('alertas').innerHTML =
        bloque('Agotados', 'exclamation-octagon', a.agotados, i => `0 ${esc(i.unidad.codigo)} · mínimo ${numero(i.existenciaMinima)}`) +
        bloque('Bajo el mínimo', 'exclamation-triangle', a.bajos, i => `${numero(i.existencia)} de ${numero(i.existenciaMinima)} ${esc(i.unidad.codigo)}`) +
        bloque('Vencidos', 'calendar-x', a.vencidos, i => 'Venció ' + fecha(i.fechaVencimiento)) +
        bloque('Por vencer (3 días)', 'calendar-event', a.porVencer, i => 'Vence ' + fecha(i.fechaVencimiento));
}

cargar().catch(e => aviso(e.message, 'error'));
