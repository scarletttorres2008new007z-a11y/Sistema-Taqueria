package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movimientos_caja")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long cajaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoCaja tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MetodoPago metodo;

    /** Positivo = entra dinero, negativo = sale. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private String concepto;

    private String referenciaTipo;

    private Long referenciaId;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();
}
