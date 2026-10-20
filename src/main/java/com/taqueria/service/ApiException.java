package com.taqueria.service;

import org.springframework.http.HttpStatus;

/** Error de negocio con un mensaje comprensible para el usuario. */
public class ApiException extends RuntimeException {

    private final HttpStatus estado;

    public ApiException(String mensaje) {
        this(HttpStatus.BAD_REQUEST, mensaje);
    }

    public ApiException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }

    public static ApiException noEncontrado(String que) {
        return new ApiException(HttpStatus.NOT_FOUND, que + " no encontrado");
    }

    public static ApiException conflicto(String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, mensaje);
    }
}
