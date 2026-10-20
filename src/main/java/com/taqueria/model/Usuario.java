package com.taqueria.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 120)
    private String nombre;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Rol rol;

    private String telefono;

    private boolean activo = true;

    private boolean demo;

    private Instant ultimoAcceso;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant actualizadoEn = Instant.now();

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
