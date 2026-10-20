package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cajas")
@Getter
@Setter
@NoArgsConstructor
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioAperturaId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoInicial;

    private Instant abiertaEn = Instant.now();

    private Long usuarioCierreId;

    private Instant cerradaEn;

    @Column(precision = 12, scale = 2)
    private BigDecimal efectivoEsperado;

    @Column(precision = 12, scale = 2)
    private BigDecimal efectivoContado;

    /** contado - esperado: negativo = faltante, positivo = sobrante. */
    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCaja estado = EstadoCaja.ABIERTA;

    private String notas;

    @Version
    private Long version;
}
