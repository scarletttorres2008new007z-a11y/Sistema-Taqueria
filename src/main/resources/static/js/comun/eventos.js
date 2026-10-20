// Escucha los cambios de pedidos en tiempo real (Server-Sent Events). Si la conexion
// falla, reintenta y mientras tanto consulta cada 20 segundos para no perder pedidos.

/**
 * @param {function} alCambiar recibe { id, numero, accion } o null cuando es una consulta periodica
 * @param {HTMLElement} indicador elemento donde mostrar "En vivo" o "Reconectando"
 */
export function escucharPedidos(alCambiar, indicador) {
    let fuente = null;
    let respaldo = null;
    let espera = 2000;

    const marcar = vivo => {
        if (!indicador) return;
        indicador.classList.toggle('en-vivo', vivo);
        indicador.innerHTML = `<span class="punto"></span>${vivo ? 'En vivo' : 'Reconectando… (actualizando cada 20 s)'}`;
    };

    const iniciarRespaldo = () => {
        if (!respaldo) respaldo = setInterval(() => alCambiar(null), 20000);
    };
    const detenerRespaldo = () => {
        clearInterval(respaldo);
        respaldo = null;
    };

    const conectar = () => {
        fuente = new EventSource('/api/pedidos/eventos');
        fuente.addEventListener('conectado', () => {
            espera = 2000;
            marcar(true);
            detenerRespaldo();
            alCambiar(null); // al reconectar se recarga por si hubo cambios mientras estaba desconectado
        });
        fuente.addEventListener('pedido', ev => {
            try { alCambiar(JSON.parse(ev.data)); } catch (e) { alCambiar(null); }
        });
        fuente.onerror = () => {
            marcar(false);
            iniciarRespaldo();
            fuente.close();
            setTimeout(conectar, espera);
            espera = Math.min(espera * 2, 30000);
        };
    };
    marcar(false);
    conectar();
    window.addEventListener('beforeunload', () => fuente && fuente.close());
}

/** Sonido corto para avisar un pedido nuevo (no requiere archivos externos). */
export function sonar() {
    try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        const o = ctx.createOscillator();
        const g = ctx.createGain();
        o.frequency.value = 880;
        g.gain.setValueAtTime(0.15, ctx.currentTime);
        g.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.4);
        o.connect(g).connect(ctx.destination);
        o.start();
        o.stop(ctx.currentTime + 0.4);
    } catch (e) { /* el navegador puede bloquear el sonido hasta que el usuario toque la pagina */ }
}
