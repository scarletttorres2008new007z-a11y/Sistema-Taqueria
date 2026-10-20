package com.taqueria.web;

import com.taqueria.dto.Dtos.AdminInicialRequest;
import com.taqueria.dto.Dtos.CambioPasswordRequest;
import com.taqueria.model.Configuracion;
import com.taqueria.security.UsuarioSesion;
import com.taqueria.service.ConfiguracionService;
import com.taqueria.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class SesionController {

    private final UsuarioService usuarios;
    private final ConfiguracionService configuracion;

    public SesionController(UsuarioService usuarios, ConfiguracionService configuracion) {
        this.usuarios = usuarios;
        this.configuracion = configuracion;
    }

    public static Map<String, Object> datosSesion(UsuarioSesion u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nombre", u.getNombre());
        m.put("rol", u.getRol());
        m.put("rolNombre", u.getRolNombre());
        m.put("permisos", u.getPermisos());
        return m;
    }

    /** Publico: indica si hay sesion y si falta crear el administrador inicial. */
    @GetMapping("/api/sesion/estado")
    public Map<String, Object> estado(Authentication auth) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean autenticado = auth != null && auth.getPrincipal() instanceof UsuarioSesion;
        m.put("autenticado", autenticado);
        if (autenticado) m.put("usuario", datosSesion((UsuarioSesion) auth.getPrincipal()));
        m.put("requiereConfiguracionInicial", usuarios.requiereAdministradorInicial());
        Configuracion c = configuracion.obtener();
        m.put("negocio", Map.of("nombre", c.getNombreComercial(), "logoUrl", c.getLogoUrl() == null ? "" : c.getLogoUrl(),
                "datosDemo", c.isDatosDemo()));
        return m;
    }

    @PostMapping("/api/sesion/password")
    public Map<String, String> cambiarPassword(@Valid @RequestBody CambioPasswordRequest req) {
        usuarios.cambiarPropiaPassword(req);
        return Map.of("mensaje", "Contraseña actualizada");
    }

    /** Solo funciona mientras la base no tenga usuarios. */
    @PostMapping("/api/configuracion-inicial/administrador")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> crearAdministrador(@Valid @RequestBody AdminInicialRequest req) {
        usuarios.crearAdministradorInicial(req);
        return Map.of("mensaje", "Administrador creado. Ya puede iniciar sesión.");
    }
}
