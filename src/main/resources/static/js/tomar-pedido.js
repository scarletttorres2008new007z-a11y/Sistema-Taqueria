// Toma de pedidos (punto de venta): menu por categorias, carrito con observaciones y
// complementos, tipo de pedido y envio a cocina o cobro directo.
import { api, consulta, nuevaClave } from './comun/api.js';
import { esc, dinero, modal, aviso, ocupado, opciones, vacio, confirmar } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';
import { cobrarVenta, ventaRegistrada } from './comun/cobro.js';

const { tiene } = await iniciarPagina({ titulo: 'Tomar pedido', permiso: 'pedidos.crear' });

const $ = id => document.getElementById(id);
const r2 = n => Math.round(n * 100) / 100;

let productos = [], categorias = [], complementos = [], mesas = [];
let categoriaActual = null;
let carrito = [];        // { clave, producto, cantidad, observaciones, complementos: [] }
let clienteId = null;
let clavePedido = nuevaClave(); // se renueva al vaciar el carrito
let ivaPct = 13;

async function cargar() {
    [productos, categorias, complementos, mesas] = await Promise.all([
        api('/api/productos'), api('/api/categorias'), api('/api/complementos'), api('/api/mesas')
    ]);
    try {
        const conf = await api('/api/configuracion');
        ivaPct = Number(conf.ivaPorcentaje);
        $('t-iva-pct').textContent = ivaPct;
    } catch (e) { /* se usa 13 % solo para la vista previa */ }
    productos = productos.filter(p => p.activo);
    categorias = categorias.filter(c => c.activa && productos.some(p => p.categoria.id === c.id));
    mesas = mesas.filter(m => m.activa);
    $('mesa').innerHTML = mesas.length ? opciones(mesas, 'id', 'nombre', null, 'Seleccione la mesa')
        : '<option value="">No hay mesas configuradas</option>';
    dibujarCategorias();
    dibujarProductos();
    dibujarCarrito();
    if (!tiene('ventas.cobrar')) $('directa').classList.add('d-none');
}

function dibujarCategorias() {
    const botones = [{ id: null, nombre: 'Todo' }, ...categorias];
    $('categorias').innerHTML = botones.map(c =>
        `<button type="button" class="btn btn-sm ${c.id === categoriaActual ? 'btn-primary' : 'btn-outline-secondary'}" data-cat="${c.id ?? ''}">${esc(c.nombre)}</button>`).join('');
}

$('categorias').addEventListener('click', ev => {
    const b = ev.target.closest('[data-cat]');
    if (!b) return;
    categoriaActual = b.dataset.cat ? Number(b.dataset.cat) : null;
    dibujarCategorias();
    dibujarProductos();
});
$('buscar').addEventListener('input', dibujarProductos);

function agotado(p) {
    return p.tipo === 'REVENTA' && p.itemInventario && Number(p.itemInventario.existencia) <= 0;
}

function dibujarProductos() {
    const texto = $('buscar').value.trim().toLowerCase();
    const lista = productos.filter(p => (categoriaActual === null || p.categoria.id === categoriaActual) &&
        (!texto || p.nombre.toLowerCase().includes(texto) || (p.codigo || '').toLowerCase().includes(texto)));
    $('productos').innerHTML = lista.length ? lista.map(p => {
        const noDisp = !p.disponible || agotado(p);
        return `<button type="button" class="producto-boton" data-id="${p.id}" ${noDisp ? 'disabled' : ''}>
            ${p.imagenUrl ? `<img src="${esc(p.imagenUrl)}" alt="">` : ''}
            <span class="nombre">${esc(p.nombre)}</span>
            ${noDisp ? `<span class="small text-danger">${agotado(p) ? 'Agotado' : 'No disponible'}</span>` : ''}
            <span class="precio">${dinero(p.precio)}</span></button>`;
    }).join('') : `<div class="tarjeta" style="grid-column:1/-1">${vacio(productos.length ? 'Ningún producto coincide' : 'No hay productos activos. Agréguelos en Productos y menú.', 'search')}</div>`;
}

$('productos').addEventListener('click', ev => {
    const b = ev.target.closest('.producto-boton');
    if (!b || b.disabled) return;
    const p = productos.find(x => x.id === Number(b.dataset.id));
    // Si ya hay una linea igual sin observaciones ni complementos, se suma la cantidad
    const igual = carrito.find(l => l.producto.id === p.id && !l.observaciones && !l.complementos.length);
    if (igual) igual.cantidad++;
    else carrito.push({ clave: nuevaClave(), producto: p, cantidad: 1, observaciones: '', complementos: [] });
    dibujarCarrito();
});

function precioLinea(l) {
    const extra = l.complementos.reduce((s, c) => s + Number(c.precioExtra), 0);
    return r2(Number(l.producto.precio) + extra);
}

function dibujarCarrito() {
    const cont = $('lineas');
    if (!carrito.length) {
        cont.innerHTML = vacio('Toque un producto para agregarlo', 'basket');
    } else {
        cont.innerHTML = carrito.map((l, i) => `<div class="linea" data-i="${i}">
            <div class="d-flex justify-content-between gap-2">
                <div class="fw-semibold">${esc(l.producto.nombre)}</div>
                <div class="num fw-semibold">${dinero(precioLinea(l) * l.cantidad)}</div>
            </div>
            ${l.complementos.length ? `<div class="small text-secondary">+ ${l.complementos.map(c => esc(c.nombre)).join(', ')}</div>` : ''}
            ${l.observaciones ? `<div class="small text-danger"><i class="bi bi-chat-left-text"></i> ${esc(l.observaciones)}</div>` : ''}
            <div class="d-flex align-items-center gap-2 mt-1">
                <div class="cantidad-control">
                    <button type="button" class="btn btn-outline-secondary" data-acc="menos" aria-label="Quitar uno"><i class="bi bi-dash"></i></button>
                    <input type="number" class="form-control form-control-sm" min="1" max="999" value="${l.cantidad}" data-acc="cantidad" aria-label="Cantidad">
                    <button type="button" class="btn btn-outline-secondary" data-acc="mas" aria-label="Agregar uno"><i class="bi bi-plus"></i></button>
                </div>
                <span class="small text-secondary">${dinero(precioLinea(l))} c/u</span>
                <button type="button" class="btn btn-sm btn-link ms-auto" data-acc="editar"><i class="bi bi-pencil"></i> Detalles</button>
                <button type="button" class="btn btn-sm btn-outline-danger" data-acc="quitar" aria-label="Quitar producto"><i class="bi bi-trash"></i></button>
            </div></div>`).join('');
    }
    const total = r2(carrito.reduce((s, l) => s + precioLinea(l) * l.cantidad, 0));
    const iva = r2(total - total / (1 + ivaPct / 100));
    $('t-cantidad').textContent = carrito.reduce((s, l) => s + l.cantidad, 0);
    $('t-total').textContent = dinero(total);
    $('t-iva').textContent = dinero(iva);
    $('m-cantidad').textContent = $('t-cantidad').textContent;
    $('m-total').textContent = dinero(total);
    $('ir-carrito').classList.toggle('invisible', !carrito.length);
    $('enviar').disabled = $('directa').disabled = !carrito.length;
}

$('lineas').addEventListener('click', ev => {
    const b = ev.target.closest('[data-acc]');
    if (!b || b.dataset.acc === 'cantidad') return;
    const i = Number(b.closest('.linea').dataset.i);
    const l = carrito[i];
    if (b.dataset.acc === 'mas') l.cantidad = Math.min(999, l.cantidad + 1);
    if (b.dataset.acc === 'menos') { l.cantidad--; if (l.cantidad < 1) carrito.splice(i, 1); }
    if (b.dataset.acc === 'quitar') carrito.splice(i, 1);
    if (b.dataset.acc === 'editar') { editarLinea(l); return; }
    dibujarCarrito();
});
$('lineas').addEventListener('change', ev => {
    if (ev.target.dataset.acc !== 'cantidad') return;
    const l = carrito[Number(ev.target.closest('.linea').dataset.i)];
    const n = Math.floor(Number(ev.target.value));
    l.cantidad = n >= 1 && n <= 999 ? n : 1;
    dibujarCarrito();
});

function editarLinea(l) {
    const aplicables = complementos.filter(c => c.activo && (!c.categoria || c.categoria.id === l.producto.categoria.id));
    const elegidos = new Set(l.complementos.map(c => c.id));
    modal({
        titulo: l.producto.nombre,
        cuerpo: `<label class="form-label" for="obs">Observaciones para cocina</label>
            <textarea class="form-control mb-3" id="obs" rows="2" maxlength="250" placeholder="Ej.: sin cebolla, salsa aparte">${esc(l.observaciones)}</textarea>
            <div class="mb-1 fw-semibold">Complementos</div>
            ${aplicables.length ? aplicables.map(c => `<div class="form-check">
                <input class="form-check-input" type="checkbox" id="comp-${c.id}" value="${c.id}" ${elegidos.has(c.id) ? 'checked' : ''}>
                <label class="form-check-label" for="comp-${c.id}">${esc(c.nombre)} <span class="text-secondary">+${dinero(c.precioExtra)}</span></label></div>`).join('')
                : '<p class="text-secondary small mb-0">No hay complementos para esta categoría.</p>'}`,
        botones: [
            { texto: 'Cancelar', clase: 'btn-outline-secondary', cerrar: true },
            { texto: 'Aplicar', clase: 'btn-primary', accion: async md => {
                l.observaciones = md.el.querySelector('#obs').value.trim();
                const ids = [...md.el.querySelectorAll('.form-check-input:checked')].map(x => Number(x.value));
                l.complementos = aplicables.filter(c => ids.includes(c.id));
                dibujarCarrito();
                md.cerrar();
            } }
        ]
    });
}

// ---------- Tipo de pedido y cliente ----------

function tipoActual() {
    return document.querySelector('input[name=tipo]:checked').value;
}
document.querySelectorAll('input[name=tipo]').forEach(r => r.addEventListener('change', () => {
    const t = tipoActual();
    $('zona-mesa').classList.toggle('d-none', t !== 'LOCAL');
    $('zona-domicilio').classList.toggle('d-none', t !== 'DOMICILIO');
    $('cliente-opcional').textContent = t === 'DOMICILIO' ? '(obligatorio)' : '(opcional)';
}));

let temporizador;
$('cliente-nombre').addEventListener('input', () => {
    clienteId = null;
    clearTimeout(temporizador);
    const q = $('cliente-nombre').value.trim();
    const sug = $('sugerencias');
    if (q.length < 2 || !tiene('clientes.ver')) { sug.classList.add('d-none'); return; }
    temporizador = setTimeout(async () => {
        try {
            const r = await api('/api/clientes' + consulta({ q, activo: true, tamano: 6 }));
            sug.innerHTML = r.contenido.map(c => `<button type="button" class="list-group-item list-group-item-action small" data-id="${c.id}">
                ${esc(c.nombre)} <span class="text-secondary">${esc(c.telefono || '')}</span></button>`).join('');
            sug.classList.toggle('d-none', !r.contenido.length);
            sug.onclick = ev => {
                const b = ev.target.closest('[data-id]');
                if (!b) return;
                const c = r.contenido.find(x => x.id === Number(b.dataset.id));
                clienteId = c.id;
                $('cliente-nombre').value = c.nombre;
                $('cliente-telefono').value = c.telefono || '';
                if (c.direccion) $('direccion').value = c.direccion;
                sug.classList.add('d-none');
            };
        } catch (e) { sug.classList.add('d-none'); }
    }, 250);
});
document.addEventListener('click', ev => { if (!ev.target.closest('#sugerencias, #cliente-nombre')) $('sugerencias').classList.add('d-none'); });

function items() {
    return carrito.map(l => ({
        productoId: l.producto.id, cantidad: l.cantidad,
        observaciones: l.observaciones || null, complementoIds: l.complementos.map(c => c.id)
    }));
}

function limpiar() {
    carrito = [];
    clienteId = null;
    clavePedido = nuevaClave();
    ['cliente-nombre', 'cliente-telefono', 'direccion', 'referencia', 'notas'].forEach(id => { $(id).value = ''; });
    $('mesa').value = '';
    dibujarCarrito();
}

$('vaciar').addEventListener('click', async () => {
    if (carrito.length && await confirmar('¿Quitar todos los productos del pedido actual?', { textoBoton: 'Vaciar' })) limpiar();
});

$('enviar').addEventListener('click', ev => ocupado(ev.currentTarget, async () => {
    const tipo = tipoActual();
    if (!carrito.length) throw new Error('Agregue al menos un producto');
    if (tipo === 'LOCAL' && mesas.length && !$('mesa').value) throw new Error('Seleccione la mesa');
    if (tipo === 'DOMICILIO' && (!$('cliente-nombre').value.trim() || !$('cliente-telefono').value.trim() || !$('direccion').value.trim())) {
        throw new Error('Para domicilio escriba el nombre, teléfono y dirección del cliente');
    }
    const datos = {
        tipo,
        mesaId: tipo === 'LOCAL' && $('mesa').value ? Number($('mesa').value) : null,
        clienteId,
        clienteNombre: $('cliente-nombre').value.trim() || null,
        clienteTelefono: $('cliente-telefono').value.trim() || null,
        direccionEntrega: tipo === 'DOMICILIO' ? $('direccion').value.trim() : null,
        referenciaEntrega: tipo === 'DOMICILIO' ? ($('referencia').value.trim() || null) : null,
        notas: $('notas').value.trim() || null,
        items: items()
    };
    const pedido = await api('/api/pedidos', { datos, clave: clavePedido });
    limpiar();
    const botones = [{ texto: 'Nuevo pedido', clase: 'btn-outline-secondary', cerrar: true }];
    if (tiene('ventas.cobrar')) {
        botones.push({ texto: 'Cobrar ahora', clase: 'btn-primary', accion: async md => {
            md.cerrar();
            const venta = await cobrarVenta({
                titulo: 'Cobrar pedido ' + pedido.numero, subtotal: Number(pedido.total),
                cliente: { id: pedido.clienteId, nombre: pedido.clienteNombre },
                enviar: (cobro, clave) => api(`/api/pedidos/${pedido.id}/venta`, { datos: cobro, clave })
            });
            if (venta) ventaRegistrada(venta);
        } });
    }
    modal({
        titulo: 'Pedido ' + pedido.numero + ' enviado a cocina',
        cuerpo: `<p class="mb-1">${esc(pedido.cantidadProductos)} producto(s) · <strong>${dinero(pedido.total)}</strong></p>
            <p class="text-secondary small mb-0">Puede cobrarlo ahora o más tarde desde <a href="/pedidos.html">Pedidos</a>.</p>`,
        botones
    });
}));

$('directa').addEventListener('click', async () => {
    if (!carrito.length) return;
    const subtotal = r2(carrito.reduce((s, l) => s + precioLinea(l) * l.cantidad, 0));
    const lista = items();
    const venta = await cobrarVenta({
        titulo: 'Venta directa', subtotal,
        cliente: { id: clienteId, nombre: $('cliente-nombre').value.trim() },
        enviar: (cobro, clave) => api('/api/ventas/directa', { datos: { items: lista, cobro }, clave })
    });
    if (venta) {
        limpiar();
        ventaRegistrada(venta);
    }
});

cargar().catch(e => aviso(e.message, 'error'));
