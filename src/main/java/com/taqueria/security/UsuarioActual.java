package com.taqueria.security;

import com.taqueria.service.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al usuario que realiza la peticion actual. */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static UsuarioSesion obtener() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u) return u;
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión");
    }

    public static Long id() {
        return obtener().getId();
    }

    public static boolean tiene(String permiso) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(permiso));
    }
}
