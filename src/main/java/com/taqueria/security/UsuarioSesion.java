package com.taqueria.security;

import com.taqueria.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

/** Usuario autenticado con sus permisos efectivos. */
public class UsuarioSesion implements UserDetails {

    private final Long id;
    private final String username;
    private final String nombre;
    private final String passwordHash;
    private final String rol;
    private final String rolNombre;
    private final boolean activo;
    private final Set<String> permisos;

    public UsuarioSesion(Usuario u) {
        this.id = u.getId();
        this.username = u.getUsername();
        this.nombre = u.getNombre();
        this.passwordHash = u.getPasswordHash();
        this.rol = u.getRol().getCodigo();
        this.rolNombre = u.getRol().getNombre();
        this.activo = u.isActivo();
        this.permisos = u.getRol().isAdministrador()
                ? new TreeSet<>(Permisos.CATALOGO.keySet())
                : new TreeSet<>(u.getRol().getPermisos());
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    public String getRolNombre() {
        return rolNombre;
    }

    public Set<String> getPermisos() {
        return permisos;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> lista = new ArrayList<>();
        lista.add(new SimpleGrantedAuthority("ROLE_" + rol));
        permisos.forEach(p -> lista.add(new SimpleGrantedAuthority(p)));
        return lista;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }
}
