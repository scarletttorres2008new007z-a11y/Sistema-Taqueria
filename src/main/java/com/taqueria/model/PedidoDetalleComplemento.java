package com.taqueria.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pedido_detalle_complementos")
@Getter
@Setter
@NoArgsConstructor
public class PedidoDetalleComplemento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private PedidoDetalle detalle;

    @Column(nullable = false)
    private Long complementoId;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioExtra;
}
