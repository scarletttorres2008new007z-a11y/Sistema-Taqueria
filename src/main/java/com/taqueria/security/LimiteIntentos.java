package com.taqueria.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bloquea temporalmente un usuario tras varios intentos fallidos de inicio de sesion. */
@Component
public class LimiteIntentos {

    private static final int MAX_INTENTOS = 5;
    private static final long BLOQUEO_SEGUNDOS = 300;

    private record Registro(int fallos, Instant hasta) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public boolean bloqueado(String usuario) {
        Registro r = registros.get(clave(usuario));
        return r != null && r.hasta() != null && r.hasta().isAfter(Instant.now());
    }

    public void fallo(String usuario) {
        registros.compute(clave(usuario), (k, r) -> {
            int fallos = (r == null || (r.hasta() != null && r.hasta().isBefore(Instant.now()))) ? 1 : r.fallos() + 1;
            return new Registro(fallos, fallos >= MAX_INTENTOS ? Instant.now().plusSeconds(BLOQUEO_SEGUNDOS) : null);
        });
    }

    public void exito(String usuario) {
        registros.remove(clave(usuario));
    }

    private static String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }
}
