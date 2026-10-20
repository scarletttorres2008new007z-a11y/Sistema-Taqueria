package com.taqueria.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "proveedores")
@Getter
@Setter
@NoArgsConstructor
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombreComercial;

    private String contacto;
    private String telefono;
    private String correo;
    private String direccion;
    private String nit;
    private String nrc;
    private String notas;

    private boolean activo = true;

    private boolean demo;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();
}
