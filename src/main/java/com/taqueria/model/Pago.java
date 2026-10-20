package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ventaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private MetodoPago metodo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(precision = 12, scale = 2)
    private BigDecimal montoRecibido;

    @Column(precision = 12, scale = 2)
    private BigDecimal cambio;

    /** Numero de autorizacion o de transferencia. Nunca datos de tarjeta. */
    private String referencia;

    private Long cajaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoPago estado = EstadoPago.APLICADO;

    @Column(nullable = false)
    private Long usuarioId;

    private String claveIdempotencia;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();
}
