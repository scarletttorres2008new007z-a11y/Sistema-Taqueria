package com.taqueria.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registro de operaciones importantes: quien, que, cuando y desde donde. */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    private String usuarioNombre;

    @Column(nullable = false, length = 80)
    private String accion;

    private String entidad;

    private Long entidadId;

    @Column(length = 1000)
    private String detalle;

    private String ip;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();
}
