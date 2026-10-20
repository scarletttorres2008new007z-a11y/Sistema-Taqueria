package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "movimientos_inventario")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private InventarioItem item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimientoInventario tipo;

    /** Positiva si aumenta la existencia, negativa si la reduce. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal existenciaAnterior;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal existenciaNueva;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    @Column(nullable = false)
    private String motivo;

    private String referenciaTipo;

    private Long referenciaId;

    private Long usuarioId;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();
}
