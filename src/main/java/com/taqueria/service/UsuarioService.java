package com.taqueria.service;

import com.taqueria.dto.Dtos.*;
import com.taqueria.model.Rol;
import com.taqueria.model.Usuario;
import com.taqueria.repository.RolRepository;
import com.taqueria.repository.UsuarioRepository;
import com.taqueria.security.Permisos;
import com.taqueria.security.UsuarioActual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** Codigo de un solo uso que solo ve quien tiene acceso a la consola del servidor. */
    private String codigoInicial;
    private int intentosCodigo;

    public UsuarioService(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder encoder,
                          AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    public boolean requiereAdministradorInicial() {
        return usuarios.count() == 0;
    }

    /**
     * Si la base no tiene usuarios, genera un codigo aleatorio y lo muestra en la consola del servidor.
     * Asi, otra persona en la misma red no puede adelantarse a crear el administrador.
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void prepararCodigoInicial() {
        if (!requiereAdministradorInicial()) return;
        SecureRandom azar = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
        }
        codigoInicial = sb.toString();
        intentosCodigo = 0;
        log.warn("""

                ==============================================================
                 CONFIGURACION INICIAL PENDIENTE
                 Abra el sistema en el navegador y cree el administrador.
                 Codigo de configuracion (un solo uso): {}
                ==============================================================""", codigoInicial);
    }

    private boolean codigoValido(String codigo) {
        if (codigoInicial == null || codigo == null) return false;
        String limpio = codigo.trim().toUpperCase(Locale.ROOT);
        return MessageDigest.isEqual(limpio.getBytes(StandardCharsets.UTF_8), codigoInicial.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Crea el primer administrador. Solo funciona mientras no exista ningun usuario,
     * asi que no puede usarse para crear cuentas despues de la instalacion.
     */
    @Transactional
    public synchronized Usuario crearAdministradorInicial(AdminInicialRequest req) {
        if (!requiereAdministradorInicial()) {
            throw ApiException.conflicto("El sistema ya tiene usuarios. Inicie sesión con el administrador.");
        }
        if (!codigoValido(req.codigoConfiguracion())) {
            if (++intentosCodigo >= 10) prepararCodigoInicial();
            throw new ApiException("El código de configuración no es correcto. Cópielo de la consola donde se inició el servidor.");
        }
        Usuario u = new Usuario();
        u.setUsername(req.username().trim());
        u.setNombre(req.nombre().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRol(roles.findByCodigo("ADMIN").orElseThrow());
        usuarios.save(u);
        auditoria.registrar(u.getId(), u.getUsername(), "Administrador inicial creado", "Usuario", u.getId(), null);
        codigoInicial = null;
        return u;
    }

    public List<Usuario> listar() {
        return usuarios.findAllByOrderByNombreAsc();
    }

    public List<Rol> roles() {
        return roles.findAll(org.springframework.data.domain.Sort.by("id"));
    }

    @Transactional
    public Usuario guardar(Long id, UsuarioRequest req) {
        Usuario u = id == null ? new Usuario() : usuarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        usuarios.buscarPorUsername(req.username().trim()).ifPresent(otro -> {
            if (!otro.getId().equals(u.getId())) throw ApiException.conflicto("Ya existe el usuario " + req.username());
        });
        Rol rol = roles.findById(req.rolId()).orElseThrow(() -> ApiException.noEncontrado("Cargo"));
        boolean eraAdminActivo = u.getId() != null && u.isActivo() && u.getRol().isAdministrador();
        boolean seraAdminActivo = req.activo() && rol.isAdministrador();
        if (eraAdminActivo && !seraAdminActivo && usuarios.contarAdministradoresActivos() <= 1) {
            throw new ApiException("Debe existir al menos un administrador activo");
        }
        if (u.getId() != null && u.getId().equals(UsuarioActual.id()) && !req.activo()) {
            throw new ApiException("No puede desactivar su propio usuario");
        }
        if (id == null && Texto.vacio(req.password())) throw new ApiException("Indique una contraseña");
        if (!Texto.vacio(req.password())) {
            if (req.password().length() < 8) throw new ApiException("La contraseña debe tener al menos 8 caracteres");
            u.setPasswordHash(encoder.encode(req.password()));
        }
        u.setUsername(req.username().trim());
        u.setNombre(req.nombre().trim());
        u.setTelefono(Texto.limpio(req.telefono()));
        u.setRol(rol);
        u.setActivo(req.activo());
        usuarios.save(u);
        auditoria.registrar(id == null ? "Usuario creado" : "Usuario modificado", "Usuario", u.getId(),
                u.getUsername() + " - cargo " + rol.getCodigo() + (u.isActivo() ? "" : " (inactivo)"));
        return u;
    }

    @Transactional
    public void restablecerPassword(Long id, String nueva) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("Usuario"));
        u.setPasswordHash(encoder.encode(nueva));
        usuarios.save(u);
        auditoria.registrar("Contraseña restablecida", "Usuario", u.getId(), u.getUsername());
    }

    @Transactional
    public void cambiarPropiaPassword(CambioPasswordRequest req) {
        Usuario u = usuarios.findById(UsuarioActual.id()).orElseThrow();
        if (!encoder.matches(req.actual(), u.getPasswordHash())) throw new ApiException("La contraseña actual no es correcta");
        u.setPasswordHash(encoder.encode(req.nueva()));
        usuarios.save(u);
        auditoria.registrar("Cambio de contraseña propia", "Usuario", u.getId(), null);
    }

    @Transactional
    public Rol actualizarPermisos(Long rolId, Set<String> permisos) {
        Rol rol = roles.findById(rolId).orElseThrow(() -> ApiException.noEncontrado("Cargo"));
        if (rol.isAdministrador()) throw new ApiException("El administrador siempre tiene todos los permisos");
        for (String p : permisos) {
            if (!Permisos.CATALOGO.containsKey(p)) throw new ApiException("Permiso desconocido: " + p);
        }
        Set<String> anteriores = new TreeSet<>(rol.getPermisos());
        rol.getPermisos().clear();
        rol.getPermisos().addAll(permisos);
        roles.save(rol);
        auditoria.registrar("Permisos modificados", "Rol", rol.getId(),
                rol.getCodigo() + ": antes " + anteriores + ", ahora " + new TreeSet<>(permisos));
        return rol;
    }

    /** id -> nombre de usuario, para mostrar quien realizo cada operacion. */
    public Map<Long, String> nombres() {
        Map<Long, String> m = new HashMap<>();
        usuarios.findAll().forEach(u -> m.put(u.getId(), u.getNombre()));
        return m;
    }
}
