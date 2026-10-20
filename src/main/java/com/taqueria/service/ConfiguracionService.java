package com.taqueria.service;

import com.taqueria.dto.Dtos.ConfiguracionRequest;
import com.taqueria.model.Configuracion;
import com.taqueria.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@Service
public class ConfiguracionService {

    private final ConfiguracionRepository repo;
    private final ArchivoService archivos;
    private final AuditoriaService auditoria;

    public ConfiguracionService(ConfiguracionRepository repo, ArchivoService archivos, AuditoriaService auditoria) {
        this.repo = repo;
        this.archivos = archivos;
        this.auditoria = auditoria;
    }

    public Configuracion obtener() {
        return repo.findById(Configuracion.ID).orElseThrow(() -> new IllegalStateException("Falta la configuración"));
    }

    @Transactional
    public Configuracion actualizar(ConfiguracionRequest req) {
        Configuracion c = obtener();
        c.setNombreComercial(req.nombreComercial().trim());
        c.setRazonSocial(Texto.limpio(req.razonSocial()));
        c.setTelefono(Texto.limpio(req.telefono()));
        c.setCorreo(Texto.limpio(req.correo()));
        c.setDireccion(Texto.limpio(req.direccion()));
        c.setNit(Texto.limpio(req.nit()));
        c.setNrc(Texto.limpio(req.nrc()));
        c.setGiro(Texto.limpio(req.giro()));
        c.setMensajeComprobante(Texto.limpio(req.mensajeComprobante()));
        c.setIvaPorcentaje(req.ivaPorcentaje());
        c.setAnchoPapelMm(req.anchoPapelMm());
        c.setActualizadoEn(Instant.now());
        repo.save(c);
        auditoria.registrar("Configuración actualizada", "Configuracion", c.getId(),
                "Nombre: " + c.getNombreComercial() + ", IVA: " + c.getIvaPorcentaje() + "%, papel: " + c.getAnchoPapelMm() + " mm");
        return c;
    }

    @Transactional
    public Configuracion subirLogo(MultipartFile archivo) {
        Configuracion c = obtener();
        c.setLogoUrl(archivos.guardarImagen(archivo, "logo"));
        repo.save(c);
        auditoria.registrar("Logotipo actualizado", "Configuracion", c.getId(), null);
        return c;
    }

    @Transactional
    public Configuracion quitarLogo() {
        Configuracion c = obtener();
        c.setLogoUrl(null);
        repo.save(c);
        return c;
    }
}
