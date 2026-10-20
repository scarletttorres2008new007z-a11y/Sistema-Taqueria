package com.taqueria.web;

import com.taqueria.dto.Dtos.*;
import com.taqueria.model.Configuracion;
import com.taqueria.model.Rol;
import com.taqueria.model.Usuario;
import com.taqueria.repository.AuditoriaRepository;
import com.taqueria.security.Permisos;
import com.taqueria.service.ComprobanteService;
import com.taqueria.service.ConfiguracionService;
import com.taqueria.service.Fechas;
import com.taqueria.service.Texto;
import com.taqueria.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;

/** Configuracion del negocio, usuarios, cargos, permisos y auditoria. */
@RestController
@RequestMapping("/api")
public class AdministracionController {

    private final ConfiguracionService configuracion;
    private final UsuarioService usuarios;
    private final AuditoriaRepository auditoria;

    public AdministracionController(ConfiguracionService configuracion, UsuarioService usuarios, AuditoriaRepository auditoria) {
        this.configuracion = configuracion;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    @GetMapping("/configuracion")
    public Configuracion configuracion() {
        return configuracion.obtener();
    }

    @PutMapping("/configuracion")
    @PreAuthorize("hasAuthority('configuracion.gestionar')")
    public Configuracion guardarConfiguracion(@Valid @RequestBody ConfiguracionRequest req) {
        if (req.anchoPapelMm() != 58 && req.anchoPapelMm() != 80) {
            throw new com.taqueria.service.ApiException("El ancho de papel debe ser 58 u 80 mm");
        }
        return configuracion.actualizar(req);
    }

    @PostMapping("/configuracion/logo")
    @PreAuthorize("hasAuthority('configuracion.gestionar')")
    public Configuracion subirLogo(@RequestParam("archivo") MultipartFile archivo) {
        return configuracion.subirLogo(archivo);
    }

    @DeleteMapping("/configuracion/logo")
    @PreAuthorize("hasAuthority('configuracion.gestionar')")
    public Configuracion quitarLogo() {
        return configuracion.quitarLogo();
    }

    /** Estado de la facturacion electronica: se informa con claridad que no esta integrada. */
    @GetMapping("/configuracion/facturacion-electronica")
    public Map<String, Object> facturacionElectronica() {
        Configuracion c = configuracion.obtener();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("integrada", false);
        m.put("estado", "PENDIENTE");
        m.put("mensaje", "El sistema emite solo comprobantes internos. La emisión de Documentos Tributarios Electrónicos (DTE) "
                + "del Ministerio de Hacienda no está integrada ni validada. Consulte docs/FACTURACION_ELECTRONICA.md.");
        m.put("leyendaComprobante", ComprobanteService.LEYENDA);
        List<String> faltantes = new ArrayList<>();
        if (Texto.vacio(c.getNit())) faltantes.add("NIT del contribuyente");
        if (Texto.vacio(c.getNrc())) faltantes.add("NRC del contribuyente");
        if (Texto.vacio(c.getRazonSocial())) faltantes.add("Nombre o razón social");
        if (Texto.vacio(c.getGiro())) faltantes.add("Actividad económica (giro)");
        faltantes.add("Autorización como emisor de DTE del Ministerio de Hacienda");
        faltantes.add("Credenciales de la API de recepción de DTE (ambiente de pruebas y producción)");
        faltantes.add("Certificado y servicio de firma electrónica de documentos");
        faltantes.add("Pruebas de emisión aprobadas en el ambiente de pruebas de Hacienda");
        m.put("faltantes", faltantes);
        return m;
    }

    // ---------- Usuarios ----------

    @GetMapping("/usuarios")
    @PreAuthorize("hasAuthority('usuarios.gestionar')")
    public List<Usuario> usuarios() {
        return usuarios.listar();
    }

    @PostMapping("/usuarios")
    @PreAuthorize("hasAuthority('usuarios.gestionar')")
    @ResponseStatus(HttpStatus.CREATED)
    public Usuario crearUsuario(@Valid @RequestBody UsuarioRequest req) {
        return usuarios.guardar(null, req);
    }

    @PutMapping("/usuarios/{id}")
    @PreAuthorize("hasAuthority('usuarios.gestionar')")
    public Usuario editarUsuario(@PathVariable Long id, @Valid @RequestBody UsuarioRequest req) {
        return usuarios.guardar(id, req);
    }

    @PostMapping("/usuarios/{id}/password")
    @PreAuthorize("hasAuthority('usuarios.gestionar')")
    public Map<String, String> restablecerPassword(@PathVariable Long id, @Valid @RequestBody PasswordRequest req) {
        usuarios.restablecerPassword(id, req.nueva());
        return Map.of("mensaje", "Contraseña restablecida");
    }

    /** Nombres de los empleados para mostrar quién hizo cada operación (sin datos sensibles). */
    @GetMapping("/usuarios/nombres")
    @PreAuthorize("isAuthenticated()")
    public Map<Long, String> nombresUsuarios() {
        return usuarios.nombres();
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('usuarios.gestionar')")
    public List<Rol> roles() {
        return usuarios.roles();
    }

    @PutMapping("/roles/{id}/permisos")
    @PreAuthorize("hasAuthority('usuarios.gestionar')")
    public Rol permisosRol(@PathVariable Long id, @Valid @RequestBody RolPermisosRequest req) {
        return usuarios.actualizarPermisos(id, req.permisos());
    }

    @GetMapping("/permisos")
    @PreAuthorize("hasAuthority('usuarios.gestionar')")
    public Map<String, String> permisos() {
        return Permisos.CATALOGO;
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasAuthority('auditoria.ver')")
    public Map<String, Object> auditoria(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                         @RequestParam(defaultValue = "0") int pagina,
                                         @RequestParam(defaultValue = "25") int tamano) {
        LocalDate d = Fechas.o(desde, Fechas.hoy().minusDays(7));
        LocalDate h = Fechas.o(hasta, Fechas.hoy());
        Fechas.validarRango(d, h);
        return ApiErrores.pagina(auditoria.buscar(Texto.limpio(q), Fechas.inicio(d), Fechas.finExclusivo(h),
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100))));
    }
}
