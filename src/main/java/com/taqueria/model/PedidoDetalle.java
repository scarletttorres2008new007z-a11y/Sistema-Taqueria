package com.taqueria.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pedido_detalles")
@Getter
@Setter
@NoArgsConstructor
public class PedidoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Pedido pedido;

    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false, length = 100)
    private String nombreProducto;

    /** Precio del producto en el momento del pedido. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioBase;

    /** Precio base mas complementos. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    private int cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    private String observaciones;

    @OneToMany(mappedBy = "detalle", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("id")
    private List<PedidoDetalleComplemento> complementos = new ArrayList<>();
}
