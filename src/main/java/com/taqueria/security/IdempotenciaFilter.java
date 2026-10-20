package com.taqueria.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Evita que una misma operacion se ejecute dos veces (doble clic, reintento por red lenta).
 * La interfaz envia la cabecera Idempotency-Key en las operaciones que crean o modifican datos.
 * La primera peticion se ejecuta y su respuesta se guarda; las repeticiones reciben la misma
 * respuesta sin volver a ejecutar nada. Si la primera todavia esta en proceso se responde 409.
 */
public class IdempotenciaFilter extends OncePerRequestFilter {

    public static final String CABECERA = "Idempotency-Key";
    private static final Pattern CLAVE_VALIDA = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final int EN_PROCESO = 0;

    private final JdbcTemplate jdbc;

    public IdempotenciaFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String m = request.getMethod();
        return !(request.getRequestURI().startsWith("/api/") && ("POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m)))
                || request.getHeader(CABECERA) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String clave = req.getHeader(CABECERA);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!CLAVE_VALIDA.matcher(clave).matches() || auth == null || !(auth.getPrincipal() instanceof UsuarioSesion u)) {
            chain.doFilter(req, res);
            return;
        }
        String ruta = req.getMethod() + " " + req.getRequestURI();
        try {
            jdbc.update("INSERT INTO solicitudes_idempotentes (clave, usuario_id, ruta, estado_http) VALUES (?, ?, ?, ?)",
                    clave, u.getId(), ruta, EN_PROCESO);
        } catch (DuplicateKeyException e) {
            responderRepetida(clave, u.getId(), ruta, res);
            return;
        }

        ContentCachingResponseWrapper envoltura = new ContentCachingResponseWrapper(res);
        boolean exito = false;
        try {
            chain.doFilter(req, envoltura);
            int estado = envoltura.getStatus();
            exito = estado >= 200 && estado < 300;
            if (exito) {
                String cuerpo = new String(envoltura.getContentAsByteArray(), StandardCharsets.UTF_8);
                jdbc.update("UPDATE solicitudes_idempotentes SET estado_http = ?, respuesta = ? WHERE clave = ?",
                        estado, cuerpo, clave);
            }
        } finally {
            if (!exito) {
                // Si la operacion fallo, se libera la clave para que el usuario pueda reintentar.
                jdbc.update("DELETE FROM solicitudes_idempotentes WHERE clave = ?", clave);
            }
            envoltura.copyBodyToResponse();
        }
    }

    private void responderRepetida(String clave, Long usuarioId, String ruta, HttpServletResponse res) throws IOException {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT usuario_id, ruta, estado_http, respuesta FROM solicitudes_idempotentes WHERE clave = ?", clave);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        if (filas.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_CONFLICT);
            res.getWriter().write("{\"mensaje\":\"La operación se está procesando. Intente de nuevo.\"}");
            return;
        }
        Map<String, Object> f = filas.get(0);
        if (!usuarioId.equals(((Number) f.get("usuario_id")).longValue()) || !ruta.equals(f.get("ruta"))) {
            res.setStatus(HttpServletResponse.SC_CONFLICT);
            res.getWriter().write("{\"mensaje\":\"Clave de operación repetida.\"}");
            return;
        }
        int estado = ((Number) f.get("estado_http")).intValue();
        if (estado == EN_PROCESO) {
            res.setStatus(HttpServletResponse.SC_CONFLICT);
            res.getWriter().write("{\"mensaje\":\"La operación ya se está procesando. Espere un momento.\"}");
            return;
        }
        res.setStatus(estado);
        res.setHeader("Idempotent-Replay", "true");
        Object cuerpo = f.get("respuesta");
        if (cuerpo != null) res.getWriter().write(cuerpo.toString());
    }
}
