// Prueba 25 (formato de impresion termica) con un navegador real.
//
// Abre un comprobante interno, genera el PDF de impresion en 58 mm y en 80 mm y verifica:
//   - que el PDF tenga una sola pagina del ancho del papel (sin cortes ni hojas en blanco),
//   - que al imprimir solo se muestre el ticket (sin menu ni botones),
//   - que el ticket tenga la leyenda de comprobante interno.
// No envia nada a ninguna impresora ni cuenta como impresion del comprobante.
// La prueba en una impresora termica fisica sigue PENDIENTE DE VALIDACION REAL.
//
// Uso (con el sistema en marcha y al menos una venta registrada):
//   npm install && npx playwright install chromium
//   set BASE_URL=http://localhost:8080   (Windows)   |  export BASE_URL=...  (Linux/macOS)
//   set USUARIO=su_usuario
//   set CLAVE=su_contraseña
//   node impresion.js [id-del-comprobante]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const USUARIO = process.env.USUARIO;
const CLAVE = process.env.CLAVE;
const SALIDA = path.join(__dirname, 'resultados');
const MM_A_PT = 72 / 25.4;

function analizarPdf(archivo) {
    const texto = fs.readFileSync(archivo).toString('latin1');
    const paginas = (texto.match(/\/Type\s*\/Page[^s]/g) || []).length;
    const caja = texto.match(/\/MediaBox\s*\[\s*0\s+0\s+([\d.]+)\s+([\d.]+)\s*\]/);
    return { paginas, anchoPt: caja ? Number(caja[1]) : NaN, altoPt: caja ? Number(caja[2]) : NaN };
}

(async () => {
    if (!USUARIO || !CLAVE) {
        console.error('Defina las variables USUARIO y CLAVE de un usuario con permiso para ver comprobantes.');
        process.exit(2);
    }
    fs.mkdirSync(SALIDA, { recursive: true });
    const navegador = await chromium.launch();
    const contexto = await navegador.newContext({ locale: 'es-SV', timezoneId: 'America/El_Salvador' });
    const pagina = await contexto.newPage();
    const errores = [];
    pagina.on('pageerror', e => errores.push(e.message));

    await pagina.goto(BASE + '/login.html');
    await pagina.fill('#usuario', USUARIO);
    await pagina.fill('#clave', CLAVE);
    await Promise.all([pagina.waitForURL(u => !u.toString().includes('login')), pagina.click('#btn-entrar')]);

    let id = process.argv[2];
    if (!id) {
        const lista = await pagina.evaluate(async () => (await fetch('/api/comprobantes?tamano=1')).json());
        if (!lista.contenido || !lista.contenido.length) throw new Error('No hay comprobantes. Registre una venta primero.');
        id = lista.contenido[0].id;
    }

    let fallos = 0;
    const comprobar = (ok, mensaje) => {
        console.log((ok ? '  OK    ' : '  FALLA ') + mensaje);
        if (!ok) fallos++;
    };

    for (const ancho of [58, 80]) {
        console.log(`Comprobante ${id} en papel de ${ancho} mm`);
        await pagina.goto(`${BASE}/comprobante.html?id=${id}`);
        await pagina.waitForSelector('#ticket .leyenda');
        await pagina.selectOption('#ancho', String(ancho));
        await pagina.waitForTimeout(300);

        const leyenda = await pagina.textContent('#ticket .leyenda');
        comprobar(/COMPROBANTE INTERNO/.test(leyenda), 'el ticket tiene la leyenda de comprobante interno');

        await pagina.emulateMedia({ media: 'print' });
        const visible = await pagina.evaluate(() => {
            const vis = sel => { const e = document.querySelector(sel); return !!e && getComputedStyle(e).display !== 'none' && e.offsetParent !== null; };
            return { menu: vis('.barra-lateral'), encabezado: vis('.encabezado'), ticket: vis('#ticket') };
        });
        comprobar(visible.ticket && !visible.menu && !visible.encabezado, 'al imprimir solo se ve el ticket');

        const archivo = path.join(SALIDA, `ticket-${ancho}mm.pdf`);
        await pagina.pdf({ path: archivo, preferCSSPageSize: true, printBackground: true });
        await pagina.emulateMedia({ media: 'screen' });
        const pdf = analizarPdf(archivo);
        comprobar(pdf.paginas === 1, `el PDF tiene una sola página (tiene ${pdf.paginas})`);
        comprobar(Math.abs(pdf.anchoPt - ancho * MM_A_PT) < 2,
            `el ancho de página es ${ancho} mm (mide ${(pdf.anchoPt / MM_A_PT).toFixed(1)} mm, alto ${(pdf.altoPt / MM_A_PT).toFixed(0)} mm)`);
        await pagina.screenshot({ path: path.join(SALIDA, `ticket-${ancho}mm.png`), fullPage: true });
    }
    comprobar(errores.length === 0, 'sin errores de JavaScript' + (errores.length ? ': ' + errores.join('; ') : ''));
    await navegador.close();
    console.log(fallos ? `\n${fallos} comprobación(es) fallaron.` : '\nFormato de impresión correcto. Archivos en ' + SALIDA);
    console.log('Recuerde: la prueba en la impresora térmica física sigue pendiente de validación real.');
    process.exit(fallos ? 1 : 0);
})().catch(e => {
    console.error('Error: ' + e.message);
    process.exit(1);
});
