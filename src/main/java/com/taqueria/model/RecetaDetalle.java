package com.taqueria.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "receta_detalles")
@Getter
@Setter
@NoArgsConstructor
public class RecetaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Receta receta;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private InventarioItem item;

    /** Cantidad por unidad de producto, expresada en "unidad". */
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private UnidadMedida unidad;
}
