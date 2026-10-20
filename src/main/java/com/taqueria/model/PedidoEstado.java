package com.taqueria.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Historial de cambios de estado de un pedido. */
@Entity
@Table(name = "pedido_estados")
@Getter
@Setter
@NoArgsConstructor
public class PedidoEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    private EstadoPedido estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estadoNuevo;

    private String motivo;

    private Long usuarioId;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();
}
