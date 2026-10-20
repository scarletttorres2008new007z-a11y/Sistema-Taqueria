package com.taqueria.web;

import com.taqueria.service.ApiException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Convierte errores en respuestas JSON comprensibles, sin exponer detalles internos. */
@RestControllerAdvice
public class ApiErrores {

    private static final Logger log = LoggerFactory.getLogger(ApiErrores.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> negocio(ApiException e) {
        return respuesta(e.getEstado(), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String primero = campos.isEmpty() ? "Datos no válidos" : campos.values().iterator().next();
        return respuesta(HttpStatus.BAD_REQUEST, "Revise los datos: " + primero, campos);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> restriccion(ConstraintViolationException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Datos no válidos: " + e.getConstraintViolations().iterator().next().getMessage(), null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> formato(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "La solicitud tiene datos con formato incorrecto", null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> permiso(Exception e) {
        return respuesta(HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta acción", null);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> concurrencia(Exception e) {
        return respuesta(HttpStatus.CONFLICT, "Otro usuario modificó este registro al mismo tiempo. Actualice e intente de nuevo.", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException e) {
        log.warn("Violacion de integridad: {}", e.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, "La operación no se pudo guardar porque entra en conflicto con datos existentes "
                + "(registro duplicado o relacionado con otros datos).", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> archivoGrande(MaxUploadSizeExceededException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "El archivo no puede superar 5 MB", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> metodo(HttpRequestMethodNotSupportedException e) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Operación no permitida", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> general(Exception e) {
        log.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intente de nuevo o consulte al administrador.", null);
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String mensaje, Map<String, String> campos) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("mensaje", mensaje);
        if (campos != null && !campos.isEmpty()) cuerpo.put("campos", campos);
        return ResponseEntity.status(estado).body(cuerpo);
    }

    static <T> Map<String, Object> pagina(org.springframework.data.domain.Page<T> p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contenido", p.getContent());
        m.put("pagina", p.getNumber());
        m.put("tamano", p.getSize());
        m.put("totalElementos", p.getTotalElements());
        m.put("totalPaginas", p.getTotalPages());
        return m;
    }

    static List<String> nada() {
        return List.of();
    }
}
