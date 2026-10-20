package com.taqueria.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cierra la sesion de un empleado si el administrador lo desactivo o le cambio el cargo
 * o los permisos de su cargo mientras tenia la sesion abierta: debe volver a entrar. Es una consulta por clave primaria en cada peticion.
 */
public class SesionVigenteFilter extends OncePerRequestFilter {

    private final JdbcTemplate jdbc;

    public SesionVigenteFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/vendor/") || uri.startsWith("/css/") || uri.startsWith("/img/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u) {
            List<Boolean> vigente = jdbc.query("""
                    SELECT u.activo, r.codigo,
                           (SELECT string_agg(rp.permiso, ',') FROM rol_permisos rp WHERE rp.rol_id = r.id) AS permisos
                    FROM usuarios u JOIN roles r ON r.id = u.rol_id WHERE u.id = ?""",
                    (rs, i) -> {
                        if (!rs.getBoolean("activo") || !u.getRol().equals(rs.getString("codigo"))) return false;
                        if ("ADMIN".equals(u.getRol())) return true;
                        String lista = rs.getString("permisos");
                        Set<String> actuales = lista == null ? Set.of() : new TreeSet<>(Arrays.asList(lista.split(",")));
                        return actuales.equals(u.getPermisos());
                    }, u.getId());
            if (vigente.isEmpty() || !vigente.get(0)) {
                SecurityContextHolder.clearContext();
                HttpSession sesion = req.getSession(false);
                if (sesion != null) sesion.invalidate();
                if (req.getRequestURI().startsWith("/api/")) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"mensaje\":\"Su usuario fue modificado o desactivado. Inicie sesión de nuevo.\"}");
                } else {
                    res.sendRedirect("/login.html");
                }
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
