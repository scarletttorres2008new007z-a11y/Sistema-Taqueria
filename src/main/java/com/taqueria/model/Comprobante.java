package com.taqueria.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Comprobante interno de venta. NO es un documento tributario electronico. */
@Entity
@Table(name = "comprobantes")
@Getter
@Setter
@NoArgsConstructor
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String numero;

    @Column(nullable = false, length = 20)
    private String tipo = "INTERNO";

    @Column(nullable = false, unique = true)
    private Long ventaId;

    @Column(updatable = false)
    private Instant fechaEmision = Instant.now();

    /** Datos del negocio copiados al emitir (si luego cambian, el comprobante no cambia). */
    @Column(nullable = false)
    private String negocioNombre;
    private String negocioDireccion;
    private String negocioTelefono;
    private String negocioNit;
    private String negocioNrc;
    private String mensaje;

    private String clienteNombre;
    private String clienteDocumento;
    private String clienteDireccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoComprobante estado = EstadoComprobante.VIGENTE;

    private int impresiones;

    private Instant ultimaImpresion;

    @Column(nullable = false)
    private Long usuarioId;
}
