package com.taqueria.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Respuesta guardada de una operacion critica para que un reintento no la repita. */
@Entity
@Table(name = "solicitudes_idempotentes")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudIdempotente {

    @Id
    @Column(length = 64)
    private String clave;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private String ruta;

    private int estadoHttp;

    @Column(columnDefinition = "text")
    private String respuesta;

    private Instant creadoEn = Instant.now();
}
