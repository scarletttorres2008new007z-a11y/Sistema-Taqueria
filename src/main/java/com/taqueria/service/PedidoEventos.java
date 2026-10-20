package com.taqueria.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Avisa en tiempo real (Server-Sent Events) a las pantallas abiertas cuando se crea o
 * cambia un pedido. El aviso sale solo despues de confirmar la transaccion en la base.
 */
@Component
public class PedidoEventos {

    /** Evento interno que publican los servicios al modificar un pedido. */
    public record PedidoCambiado(Long id, String numero, String accion) {
    }

    private static final Logger log = LoggerFactory.getLogger(PedidoEventos.class);
    private final List<SseEmitter> emisores = new CopyOnWriteArrayList<>();

    public SseEmitter suscribir() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emisores.add(emitter);
        emitter.onCompletion(() -> emisores.remove(emitter));
        emitter.onTimeout(() -> emisores.remove(emitter));
        emitter.onError(e -> emisores.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("conectado").data("ok"));
        } catch (IOException e) {
            emisores.remove(emitter);
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCambiar(PedidoCambiado evento) {
        enviar("pedido", Map.of("id", evento.id(), "numero", evento.numero(), "accion", evento.accion()));
    }

    /** Mantiene viva la conexion a traves de proxies y detecta clientes desconectados. */
    @Scheduled(fixedRate = 25000)
    public void latido() {
        enviar("latido", "{}");
    }

    private void enviar(String nombre, Object datos) {
        for (SseEmitter e : emisores) {
            try {
                e.send(SseEmitter.event().name(nombre).data(datos));
            } catch (Exception ex) {
                log.debug("Cliente de eventos desconectado");
                emisores.remove(e);
            }
        }
    }
}
