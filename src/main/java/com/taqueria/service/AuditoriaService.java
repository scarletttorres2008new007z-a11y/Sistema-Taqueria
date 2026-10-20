package com.taqueria.service;

import com.taqueria.model.Auditoria;
import com.taqueria.repository.AuditoriaRepository;
import com.taqueria.security.UsuarioSesion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Registra quien realizo cada operacion importante. */
@Service
public class AuditoriaService {

    private final AuditoriaRepository repo;

    public AuditoriaService(AuditoriaRepository repo) {
        this.repo = repo;
    }

    public void registrar(String accion, String entidad, Long entidadId, String detalle) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u) {
            registrar(u.getId(), u.getUsername(), accion, entidad, entidadId, detalle);
        } else {
            registrar(null, auth == null ? "sistema" : auth.getName(), accion, entidad, entidadId, detalle);
        }
    }

    public void registrar(Long usuarioId, String usuario, String accion, String entidad, Long entidadId, String detalle) {
        Auditoria a = new Auditoria();
        a.setUsuarioId(usuarioId);
        a.setUsuarioNombre(usuario);
        a.setAccion(accion);
        a.setEntidad(entidad);
        a.setEntidadId(entidadId);
        a.setDetalle(detalle != null && detalle.length() > 1000 ? detalle.substring(0, 1000) : detalle);
        a.setIp(ipActual());
        repo.save(a);
    }

    private static String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }
}
