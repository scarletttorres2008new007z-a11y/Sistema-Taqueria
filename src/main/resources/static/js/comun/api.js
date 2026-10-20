// Cliente de la API: agrega el token CSRF, la clave de idempotencia y traduce errores.

function leerCookie(nombre) {
    const par = document.cookie.split('; ').find(c => c.startsWith(nombre + '='));
    return par ? decodeURIComponent(par.split('=')[1]) : null;
}

/** Clave unica para que un reintento de la misma operacion no la repita en el servidor. */
export function nuevaClave() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    const b = new Uint8Array(16);
    crypto.getRandomValues(b);
    return Array.from(b, x => x.toString(16).padStart(2, '0')).join('');
}

export class ErrorApi extends Error {
    constructor(mensaje, estado, campos) {
        super(mensaje);
        this.estado = estado;
        this.campos = campos || {};
    }
}

/**
 * Llama a la API.
 * @param {string} ruta ruta que empieza con /api
 * @param {object} op { metodo, datos, formulario (FormData), clave (Idempotency-Key) }
 */
export async function api(ruta, op = {}) {
    const metodo = op.metodo || (op.datos || op.formulario ? 'POST' : 'GET');
    const cabeceras = { 'Accept': 'application/json' };
    if (metodo !== 'GET') {
        const token = leerCookie('XSRF-TOKEN');
        if (token) cabeceras['X-XSRF-TOKEN'] = token;
    }
    if (op.clave) cabeceras['Idempotency-Key'] = op.clave;
    let cuerpo;
    if (op.formulario) {
        cuerpo = op.formulario;
    } else if (op.datos !== undefined) {
        cabeceras['Content-Type'] = 'application/json';
        cuerpo = JSON.stringify(op.datos);
    }
    let r;
    try {
        r = await fetch(ruta, { method: metodo, headers: cabeceras, body: cuerpo, credentials: 'same-origin' });
    } catch (e) {
        throw new ErrorApi('No hay conexión con el servidor. Revise la red e intente de nuevo.', 0);
    }
    if (r.status === 401 && !ruta.startsWith('/api/sesion')) {
        location.href = '/login.html?volver=' + encodeURIComponent(location.pathname + location.search);
        throw new ErrorApi('Su sesión terminó. Inicie sesión de nuevo.', 401);
    }
    if (r.status === 204) return null;
    const tipo = r.headers.get('Content-Type') || '';
    const datos = tipo.includes('application/json') ? await r.json() : await r.text();
    if (!r.ok) {
        const mensaje = (datos && datos.mensaje) || 'No se pudo completar la operación (error ' + r.status + ')';
        throw new ErrorApi(mensaje, r.status, datos && datos.campos);
    }
    return datos;
}

/** Construye una cadena de consulta omitiendo valores vacios. */
export function consulta(parametros) {
    const p = new URLSearchParams();
    Object.entries(parametros).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== '') p.append(k, v);
    });
    const s = p.toString();
    return s ? '?' + s : '';
}
