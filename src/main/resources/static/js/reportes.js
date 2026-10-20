// Reportes con datos reales de la base: tabla, grafico y exportacion a CSV, Excel y PDF.
import { api, consulta } from './comun/api.js';
import { esc, dinero, numero, fecha, fechaHora, hoyISO, aviso, vacio, ocupado } from './comun/ui.js';
import { iniciarPagina } from './comun/layout.js';

const { negocio } = await iniciarPagina({ titulo: 'Reportes', permiso: 'reportes.ver' });
const $ = id => document.getElementById(id);
const form = $('filtros');
const AGRUPABLES = ['ventas', 'utilidad'];
let reporte = null, grafico = null;

const tipos = await api('/api/reportes');
form.tipo.innerHTML = Object.entries(tipos).map(([k, v]) => `<option value="${k}">${esc(v)}</option>`).join('');
form.desde.value = hoyISO().slice(0, 8) + '01';
form.hasta.value = hoyISO();
const verAgrupacion = () => { form.agrupacion.closest('div').classList.toggle('invisible', !AGRUPABLES.includes(form.tipo.value)); };
form.tipo.addEventListener('change', verAgrupacion);
verAgrupacion();

function formato(valor, tipo) {
    if (valor === null || valor === undefined) return '';
    if (tipo === 'dinero') return dinero(valor);
    if (tipo === 'numero') return numero(valor);
    if (tipo === 'fecha') return fecha(String(valor).slice(0, 10));
    if (tipo === 'fechahora') return fechaHora(valor);
    const texto = String(valor);
    if (/^\d{4}-\d{2}-\d{2}$/.test(texto)) return fecha(texto);
    if (/^\d{4}-\d{2}$/.test(texto)) return texto.slice(5) + '/' + texto.slice(0, 4);
    return texto;
}

function parametros() {
    return { desde: form.desde.value, hasta: form.hasta.value, agrupacion: AGRUPABLES.includes(form.tipo.value) ? form.agrupacion.value : '' };
}

form.addEventListener('submit', ev => {
    ev.preventDefault();
    ocupado(form.querySelector('[type=submit]'), generar);
});

async function generar() {
    const p = parametros();
    reporte = await api(`/api/reportes/${form.tipo.value}` + consulta(p));
    $('resultado').hidden = false;
    $('titulo').innerHTML = `<i class="bi bi-table"></i> ${esc(reporte.titulo)} <span class="small text-secondary fw-normal">${fecha(reporte.desde)} al ${fecha(reporte.hasta)}</span>`;
    $('explicacion').textContent = reporte.explicacion;
    $('csv').href = `/api/reportes/${reporte.tipo}/csv` + consulta(p);
    const cols = reporte.columnas;
    $('cabecera').innerHTML = `<tr>${cols.map(c => `<th class="${c.tipo === 'dinero' || c.tipo === 'numero' ? 'num' : ''}">${esc(c.titulo)}</th>`).join('')}</tr>`;
    $('filas').innerHTML = reporte.filas.length ? reporte.filas.map(f => `<tr>${cols.map(c =>
        `<td class="${c.tipo === 'dinero' || c.tipo === 'numero' ? 'num' : ''}">${esc(formato(f[c.clave], c.tipo))}</td>`).join('')}</tr>`).join('')
        : `<tr><td colspan="${cols.length}">${vacio('No hay datos en este período')}</td></tr>`;
    const t = reporte.totales || {};
    $('totales').innerHTML = Object.keys(t).length && reporte.filas.length ? `<tr class="fw-semibold">${cols.map((c, i) =>
        `<td class="${c.tipo === 'dinero' || c.tipo === 'numero' ? 'num' : ''}">${i === 0 ? 'Total' : esc(formato(t[c.clave], c.tipo))}</td>`).join('')}</tr>` : '';
    dibujarGrafico();
}

/** Grafico de barras de la primera columna de dinero (o numero) frente a la primera columna de texto. */
function dibujarGrafico() {
    grafico?.destroy();
    const etiqueta = reporte.columnas.find(c => c.tipo === 'texto' || c.tipo === 'fecha');
    const valor = reporte.columnas.find(c => c.tipo === 'dinero') || reporte.columnas.find(c => c.tipo === 'numero');
    const zona = $('zona-grafico');
    if (!window.Chart || !etiqueta || !valor || reporte.filas.length < 2 || reporte.filas.length > 60) { zona.hidden = true; return; }
    zona.hidden = false;
    const filas = reporte.filas.slice(0, 60);
    $('grafico').setAttribute('aria-label', `${valor.titulo} por ${etiqueta.titulo}`);
    grafico = new Chart($('grafico'), {
        type: 'bar',
        data: { labels: filas.map(f => formato(f[etiqueta.clave], etiqueta.tipo)),
            datasets: [{ label: valor.titulo, data: filas.map(f => Number(f[valor.clave] || 0)), backgroundColor: '#c2410c', hoverBackgroundColor: '#9a3412', borderRadius: 4, maxBarThickness: 36 }] },
        options: { responsive: true, maintainAspectRatio: false,
            plugins: { legend: { display: false }, title: { display: true, text: valor.titulo, align: 'start', color: '#6b625a' },
                tooltip: { callbacks: { label: i => formato(i.raw, valor.tipo) } } },
            scales: { y: { beginAtZero: true, grid: { color: 'rgba(31,27,22,.08)' }, border: { display: false } }, x: { grid: { display: false } } } }
    });
}

function matriz() {
    const cols = reporte.columnas;
    const cuerpo = reporte.filas.map(f => cols.map(c => formato(f[c.clave], c.tipo)));
    const t = reporte.totales || {};
    if (Object.keys(t).length && reporte.filas.length) cuerpo.push(cols.map((c, i) => i === 0 ? 'Total' : formato(t[c.clave], c.tipo)));
    return { cabecera: cols.map(c => c.titulo), cuerpo };
}

$('excel').addEventListener('click', () => {
    if (!reporte || !window.XLSX) return aviso('No se pudo cargar el generador de Excel', 'error');
    const cols = reporte.columnas;
    // En Excel los montos y cantidades se guardan como numeros para poder sumarlos
    const filas = reporte.filas.map(f => cols.map(c => (c.tipo === 'dinero' || c.tipo === 'numero') && f[c.clave] !== null ? Number(f[c.clave]) : formato(f[c.clave], c.tipo)));
    const hoja = XLSX.utils.aoa_to_sheet([[reporte.titulo], [`Del ${fecha(reporte.desde)} al ${fecha(reporte.hasta)}`], [], cols.map(c => c.titulo), ...filas]);
    hoja['!cols'] = cols.map(() => ({ wch: 18 }));
    const libro = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(libro, hoja, 'Reporte');
    XLSX.writeFile(libro, `reporte-${reporte.tipo}-${reporte.desde}-a-${reporte.hasta}.xlsx`);
});

$('pdf').addEventListener('click', () => {
    if (!reporte || !window.jspdf) return aviso('No se pudo cargar el generador de PDF', 'error');
    const { jsPDF } = window.jspdf;
    const { cabecera, cuerpo } = matriz();
    const doc = new jsPDF({ orientation: cabecera.length > 5 ? 'landscape' : 'portrait', unit: 'mm', format: 'letter' });
    doc.setFontSize(13);
    doc.text(negocio.nombre, 14, 15);
    doc.setFontSize(11);
    doc.text(`${reporte.titulo} - del ${fecha(reporte.desde)} al ${fecha(reporte.hasta)}`, 14, 22);
    doc.setFontSize(8);
    const exp = doc.splitTextToSize(reporte.explicacion, doc.internal.pageSize.getWidth() - 28);
    doc.text(exp, 14, 28);
    const derecha = {};
    reporte.columnas.forEach((c, i) => { if (c.tipo === 'dinero' || c.tipo === 'numero') derecha[i] = { halign: 'right' }; });
    doc.autoTable({ head: [cabecera], body: cuerpo, startY: 30 + exp.length * 3.5, styles: { fontSize: 8 }, headStyles: { fillColor: [194, 65, 12] }, columnStyles: derecha,
        didDrawPage: () => { doc.setFontSize(7); doc.text('Generado el ' + fechaHora(new Date().toISOString()), 14, doc.internal.pageSize.getHeight() - 8); } });
    doc.save(`reporte-${reporte.tipo}-${reporte.desde}-a-${reporte.hasta}.pdf`);
});
