package com.taqueria.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.UUID;

/** Guarda archivos subidos validando su contenido real (no solo la extension). */
@Service
public class ArchivoService {

    private final Path raiz;

    public ArchivoService(@Value("${taqueria.uploads-dir}") String uploadsDir) {
        this.raiz = Path.of(uploadsDir).toAbsolutePath().normalize();
    }

    /** Guarda una imagen PNG, JPG o WEBP. Devuelve la URL publica. */
    public String guardarImagen(MultipartFile archivo, String carpeta) {
        return guardar(archivo, carpeta, false);
    }

    /** Guarda una imagen o un PDF (comprobantes de gastos). */
    public String guardarImagenOPdf(MultipartFile archivo, String carpeta) {
        return guardar(archivo, carpeta, true);
    }

    private String guardar(MultipartFile archivo, String carpeta, boolean permitirPdf) {
        if (archivo == null || archivo.isEmpty()) throw new ApiException("Seleccione un archivo");
        if (archivo.getSize() > 5 * 1024 * 1024) throw new ApiException("El archivo no puede superar 5 MB");
        String extension;
        try (InputStream in = archivo.getInputStream()) {
            byte[] cabecera = in.readNBytes(12);
            extension = detectarTipo(cabecera, permitirPdf);
        } catch (IOException e) {
            throw new ApiException("No se pudo leer el archivo");
        }
        if (extension == null) {
            throw new ApiException(permitirPdf ? "Solo se permiten imágenes PNG, JPG, WEBP o archivos PDF"
                    : "Solo se permiten imágenes PNG, JPG o WEBP");
        }
        String nombre = UUID.randomUUID() + "." + extension;
        Path destino = raiz.resolve(carpeta).resolve(nombre).normalize();
        if (!destino.startsWith(raiz)) throw new ApiException("Ruta no válida");
        try {
            Files.createDirectories(destino.getParent());
            try (InputStream in = archivo.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ApiException("No se pudo guardar el archivo");
        }
        return "/archivos/" + carpeta + "/" + nombre;
    }

    static String detectarTipo(byte[] b, boolean permitirPdf) {
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) return "png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        if (permitirPdf && b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "pdf";
        return null;
    }
}
