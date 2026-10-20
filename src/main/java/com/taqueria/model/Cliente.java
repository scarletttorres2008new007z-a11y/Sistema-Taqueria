package com.taqueria.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    private String telefono;
    private String direccion;
    private String correo;
    /** DUI o NIT. */
    private String documento;
    private String nrc;
    private String notas;

    private boolean activo = true;

    private boolean demo;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant actualizadoEn = Instant.now();

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
