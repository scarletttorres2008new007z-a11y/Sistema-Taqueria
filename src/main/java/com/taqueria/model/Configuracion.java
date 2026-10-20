package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Datos comerciales del negocio. Siempre existe una sola fila con id = 1. */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
public class Configuracion {

    public static final long ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombreComercial;

    private String razonSocial;
    private String logoUrl;
    private String telefono;
    private String correo;
    private String direccion;
    private String nit;
    private String nrc;
    private String giro;
    private String mensajeComprobante;

    /** Porcentaje de IVA incluido en los precios del menu (13 en El Salvador). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ivaPorcentaje;

    /** Ancho del papel de la impresora termica: 58 u 80 mm. */
    @Column(name = "ancho_papel_mm", nullable = false)
    private int anchoPapelMm = 80;

    /** true mientras el sistema tenga datos de demostracion cargados. */
    private boolean datosDemo;

    private Instant actualizadoEn = Instant.now();
}
