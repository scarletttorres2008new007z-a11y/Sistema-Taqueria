package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Gasto operativo. Las compras de inventario se registran en Compras, no aqui. */
@Entity
@Table(name = "gastos")
@Getter
@Setter
@NoArgsConstructor
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private CategoriaGasto categoria;

    @Column(nullable = false)
    private String concepto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MetodoPago metodoPago;

    private boolean pagadoDesdeCaja;

    private String numeroComprobante;

    private String comprobanteUrl;

    private boolean anulado;

    private String motivoAnulacion;

    @Column(nullable = false)
    private Long usuarioId;

    private boolean demo;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();
}
