package com.taqueria.model;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Opcion que se agrega a un producto (queso extra, doble carne...). */
@Entity
@Table(name = "complementos")
@Getter
@Setter
@NoArgsConstructor
public class Complemento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioExtra = BigDecimal.ZERO;

    /** Si es null aplica a todos los productos. */
    @ManyToOne(fetch = FetchType.EAGER)
    private Categoria categoria;

    private boolean activo = true;
}
