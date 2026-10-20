// Productos, categorias, complementos y mesas. Nada se borra fisicamente: se desactiva.
import { api } from './comun/api.js';
import { esc, dinero, nombre, estado, aviso, confirmar, formulario, campo, opciones, tablaLocal, modal, vacio } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { tiene } = await iniciarPagina({ titulo: 'Productos y menú', permiso: 'productos.ver' });
const $ = id => document.getElementById(id);
const gestiona = tiene('productos.gestionar');
const gestionaMesas = tiene('configuracion.gestionar');
if (!gestiona) document.querySelectorAll('.gestionar').forEach(b => b.classList.add('d-none'));
if (!gestionaMesas) $('nueva-mesa').classList.add('d-none');

let productos = [], categorias = [], complementos = [], mesas = [], items = [];
let tablaProductos;

async function cargar() {
    [productos, categorias, complementos, mesas] = await Promise.all([
        api('/api/productos'), api('/api/categorias'), api('/api/complementos'), api('/api/mesas')
    ]);
    if (gestiona) items = await api('/api/inventario').catch(() => []);
    $('filtro-categoria').innerHTML = '<option value="">Todas las categorías</option>' + opciones(categorias, 'id', 'nombre');
    dibujarProductos();
    dibujarCategorias();
    dibujarComplementos();
    dibujarMesas();
}

// ---------- Productos ----------

function filasProductos() {
    const cat = $('filtro-categoria').value;
    return productos.filter(p => ($('ver-inactivos').checked || p.activo) && (!cat || String(p.categoria.id) === cat));
}

function dibujarProductos() {
    const fila = p => `<tr class="${p.activo ? '' : 'text-secondary'}">
        <td class="text-nowrap">${esc(p.codigo)}</td>
        <td><div class="d-flex align-items-center gap-2">${p.imagenUrl ? `<img src="${esc(p.imagenUrl)}" alt="" width="36" height="36" style="object-fit:cover;border-radius:6px">` : ''}
            <div><div class="fw-semibold">${esc(p.nombre)}</div>${p.demo ? '<span class="badge text-bg-warning">Demo</span>' : ''}</div></div></td>
        <td>${esc(p.categoria.nombre)}</td><td>${esc(nombre(p.tipo))}</td>
        <td class="num">${dinero(p.precio)}</td><td class="num">${dinero(p.costoEstimado)}</td>
        <td class="small">${p.tipo === 'REVENTA' ? (p.itemInventario ? esc(p.itemInventario.nombre) : '<span class="text-danger">Sin artículo</span>') : '<a href="/recetas.html">Por receta</a>'}</td>
        <td>${!p.activo ? estado('INACTIVO', 'Inactivo') : p.disponible ? estado('OK', 'Disponible') : estado('PENDIENTE', 'No disponible')}</td>
        <td class="text-end text-nowrap">${gestiona ? `<button class="btn btn-sm btn-outline-primary" data-editar="${p.id}">Editar</button>
            ${p.activo ? `<button class="btn btn-sm btn-outline-danger" data-desactivar="${p.id}">Desactivar</button>` : ''}` : ''}</td></tr>`;
    const filtro = (p, t) => p.nombre.toLowerCase().includes(t) || (p.codigo || '').toLowerCase().includes(t);
    if (tablaProductos) tablaProductos.actualizar(filasProductos());
    else tablaProductos = tablaLocal({ cuerpo: $('filas-productos'), pie: $('pie-productos'), buscador: $('buscar-productos'),
        filas: filasProductos(), fila, filtro, vacioTexto: 'No hay productos. Pulse "Nuevo producto" para crear el primero.' });
}
$('filtro-categoria').addEventListener('change', dibujarProductos);
$('ver-inactivos').addEventListener('change', dibujarProductos);

function editarProducto(p) {
    const html = `<div class="row g-2">
        ${campo('nombre', 'Nombre', { col: 'col-md-8', extra: 'maxlength="120" required' })}
        ${campo('codigo', 'Código', { col: 'col-md-4', extra: 'maxlength="30"', ayuda: 'Vacío = se genera automáticamente' })}
        ${campo('descripcion', 'Descripción', { tipo: 'textarea', extra: 'maxlength="500"' })}
        ${campo('categoriaId', 'Categoría', { col: 'col-md-6', extra: 'data-numero required', opcionesHtml: opciones(categorias.filter(c => c.activa || (p && p.categoria.id === c.id)), 'id', 'nombre', null, 'Seleccione') })}
        ${campo('tipo', 'Tipo', { col: 'col-md-6', opcionesHtml: '<option value="PREPARADO">Preparado (descuenta por receta)</option><option value="REVENTA">Reventa (bebida u otro artículo del inventario)</option><option value="COMBO">Combo (descuenta por receta)</option>' })}
        ${campo('precio', 'Precio de venta (IVA incluido)', { tipo: 'number', col: 'col-md-6', extra: 'min="0.01" step="0.01" required' })}
        ${campo('costoEstimado', 'Costo estimado', { tipo: 'number', col: 'col-md-6', extra: 'min="0" step="0.01"', ayuda: 'Se usa si no hay receta configurada' })}
        <div class="col-12" id="zona-item">${campo('itemInventarioId', 'Artículo del inventario que se descuenta', { extra: 'data-numero', opcionesHtml: opciones(items.filter(i => i.activo), 'id', i => `${i.nombre} (${i.unidad.codigo})`, null, 'Seleccione') })}</div>
        ${campo('activo', 'Activo', { tipo: 'checkbox', col: 'col-6' })}
        ${campo('disponible', 'Disponible para vender hoy', { tipo: 'checkbox', col: 'col-6' })}
        ${p ? `<div class="col-12"><label class="form-label small mb-1" for="imagen">Imagen (PNG, JPG o WebP, máx. 5 MB)</label>
            <input class="form-control" type="file" id="imagen" accept="image/png,image/jpeg,image/webp"></div>` : ''}
    </div>`;
    const valores = p ? { ...p, categoriaId: p.categoria.id, itemInventarioId: p.itemInventario ? p.itemInventario.id : '' }
        : { tipo: 'PREPARADO', activo: true, disponible: true, costoEstimado: 0 };
    formulario({
        titulo: p ? 'Editar ' + p.nombre : 'Nuevo producto', html, valores, tamano: 'modal-lg',
        alAbrir: form => {
            const ver = () => form.querySelector('#zona-item').classList.toggle('d-none', form.tipo.value !== 'REVENTA');
            form.tipo.addEventListener('change', ver);
            ver();
        },
        guardar: async (datos, form) => {
            if (datos.tipo !== 'REVENTA') datos.itemInventarioId = null;
            const guardado = await api('/api/productos' + (p ? '/' + p.id : ''), { metodo: p ? 'PUT' : 'POST', datos });
            const archivo = form.querySelector('#imagen');
            if (archivo && archivo.files.length) {
                const fd = new FormData();
                fd.append('archivo', archivo.files[0]);
                await api(`/api/productos/${guardado.id}/imagen`, { formulario: fd });
            }
            aviso('Producto guardado');
            await cargar();
            if (!p && datos.tipo !== 'REVENTA') {
                modal({ titulo: 'Producto creado', cuerpo: `<p>El producto <strong>${esc(guardado.nombre)}</strong> todavía no tiene receta.
                    Sin receta, al venderlo no se descuentan ingredientes del inventario.</p>`,
                    botones: [{ texto: 'Más tarde', clase: 'btn-outline-secondary', cerrar: true },
                        { texto: 'Configurar receta', clase: 'btn-primary', accion: async () => { location.href = '/recetas.html?producto=' + guardado.id; } }] });
            }
        }
    });
}

$('nuevo-producto').addEventListener('click', () => {
    if (!categorias.some(c => c.activa)) { aviso('Primero cree una categoría', 'alerta'); return; }
    editarProducto(null);
});
$('filas-productos').addEventListener('click', async ev => {
    const e = ev.target.closest('[data-editar]');
    const d = ev.target.closest('[data-desactivar]');
    if (e) editarProducto(productos.find(p => p.id === Number(e.dataset.editar)));
    if (d) {
        const p = productos.find(x => x.id === Number(d.dataset.desactivar));
        if (!await confirmar(`¿Desactivar "${p.nombre}"? Dejará de aparecer en el menú, pero su historial de ventas se conserva.`, { textoBoton: 'Desactivar', peligro: true })) return;
        try { await api(`/api/productos/${p.id}/desactivar`, { metodo: 'POST' }); aviso('Producto desactivado'); cargar(); } catch (err) { aviso(err.message, 'error'); }
    }
});

// ---------- Categorias ----------

function dibujarCategorias() {
    $('filas-categorias').innerHTML = categorias.length ? categorias.map(c => `<tr>
        <td>${c.orden}</td><td class="fw-semibold">${esc(c.nombre)}</td>
        <td>${productos.filter(p => p.categoria.id === c.id && p.activo).length}</td>
        <td>${c.activa ? estado('OK', 'Activa') : estado('INACTIVO', 'Inactiva')}</td>
        <td class="text-end">${gestiona ? `<button class="btn btn-sm btn-outline-primary" data-cat="${c.id}">Editar</button>` : ''}</td></tr>`).join('')
        : `<tr><td colspan="5">${vacio('No hay categorías')}</td></tr>`;
}
function editarCategoria(c) {
    formulario({
        titulo: c ? 'Editar categoría' : 'Nueva categoría',
        html: `<div class="row g-2">${campo('nombre', 'Nombre', { col: 'col-8', extra: 'maxlength="80" required' })}
            ${campo('orden', 'Orden en el menú', { tipo: 'number', col: 'col-4', extra: 'step="1"' })}
            ${campo('activa', 'Activa', { tipo: 'checkbox' })}</div>`,
        valores: c || { orden: categorias.length + 1, activa: true },
        guardar: async datos => {
            datos.orden = datos.orden || 0;
            await api('/api/categorias' + (c ? '/' + c.id : ''), { metodo: c ? 'PUT' : 'POST', datos });
            aviso('Categoría guardada');
            cargar();
        }
    });
}
$('nueva-categoria').addEventListener('click', () => editarCategoria(null));
$('filas-categorias').addEventListener('click', ev => {
    const b = ev.target.closest('[data-cat]');
    if (b) editarCategoria(categorias.find(c => c.id === Number(b.dataset.cat)));
});

// ---------- Complementos ----------

function dibujarComplementos() {
    $('filas-complementos').innerHTML = complementos.length ? complementos.map(c => `<tr>
        <td class="fw-semibold">${esc(c.nombre)}</td><td class="num">${dinero(c.precioExtra)}</td>
        <td>${c.categoria ? esc(c.categoria.nombre) : 'Todos los productos'}</td>
        <td>${c.activo ? estado('OK', 'Activo') : estado('INACTIVO', 'Inactivo')}</td>
        <td class="text-end">${gestiona ? `<button class="btn btn-sm btn-outline-primary" data-comp="${c.id}">Editar</button>` : ''}</td></tr>`).join('')
        : `<tr><td colspan="5">${vacio('No hay complementos')}</td></tr>`;
}
function editarComplemento(c) {
    formulario({
        titulo: c ? 'Editar complemento' : 'Nuevo complemento',
        html: `<div class="row g-2">${campo('nombre', 'Nombre', { col: 'col-8', extra: 'maxlength="80" required' })}
            ${campo('precioExtra', 'Precio extra', { tipo: 'number', col: 'col-4', extra: 'min="0" step="0.01" required' })}
            ${campo('categoriaId', 'Aplica a la categoría', { extra: 'data-numero', opcionesHtml: opciones(categorias, 'id', 'nombre', null, 'Todas las categorías') })}
            ${campo('activo', 'Activo', { tipo: 'checkbox' })}</div>`,
        valores: c ? { ...c, categoriaId: c.categoria ? c.categoria.id : '' } : { precioExtra: 0, activo: true },
        guardar: async datos => {
            await api('/api/complementos' + (c ? '/' + c.id : ''), { metodo: c ? 'PUT' : 'POST', datos });
            aviso('Complemento guardado');
            cargar();
        }
    });
}
$('nuevo-complemento').addEventListener('click', () => editarComplemento(null));
$('filas-complementos').addEventListener('click', ev => {
    const b = ev.target.closest('[data-comp]');
    if (b) editarComplemento(complementos.find(c => c.id === Number(b.dataset.comp)));
});

// ---------- Mesas ----------

function dibujarMesas() {
    $('filas-mesas').innerHTML = mesas.length ? mesas.map(m => `<tr>
        <td class="fw-semibold">${esc(m.nombre)}</td><td class="num">${m.capacidad}</td>
        <td>${m.activa ? estado('OK', 'Activa') : estado('INACTIVO', 'Inactiva')}</td>
        <td class="text-end">${gestionaMesas ? `<button class="btn btn-sm btn-outline-primary" data-mesa="${m.id}">Editar</button>` : ''}</td></tr>`).join('')
        : `<tr><td colspan="4">${vacio('No hay mesas. Sin mesas, los pedidos en el local no piden número de mesa.')}</td></tr>`;
}
function editarMesa(m) {
    formulario({
        titulo: m ? 'Editar mesa' : 'Nueva mesa',
        html: `<div class="row g-2">${campo('nombre', 'Nombre', { col: 'col-8', extra: 'maxlength="40" required' })}
            ${campo('capacidad', 'Capacidad', { tipo: 'number', col: 'col-4', extra: 'min="1" max="50" step="1"' })}
            ${campo('activa', 'Activa', { tipo: 'checkbox' })}</div>`,
        valores: m || { nombre: 'Mesa ' + (mesas.length + 1), capacidad: 4, activa: true },
        guardar: async datos => {
            datos.capacidad = datos.capacidad || 1;
            await api('/api/mesas' + (m ? '/' + m.id : ''), { metodo: m ? 'PUT' : 'POST', datos });
            aviso('Mesa guardada');
            cargar();
        }
    });
}
$('nueva-mesa').addEventListener('click', () => editarMesa(null));
$('filas-mesas').addEventListener('click', ev => {
    const b = ev.target.closest('[data-mesa]');
    if (b) editarMesa(mesas.find(m => m.id === Number(b.dataset.mesa)));
});

cargar().catch(e => aviso(e.message, 'error'));
