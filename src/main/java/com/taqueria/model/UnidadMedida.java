package com.taqueria.model;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "unidades_medida")
@Getter
@Setter
@NoArgsConstructor
public class UnidadMedida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String codigo;

    @Column(nullable = false, length = 40)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoUnidad tipo;

    /** Cantidad de unidades base (g, ml o unidad) que contiene. */
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal factor;

    public boolean esCompatibleCon(UnidadMedida otra) {
        return otra != null && tipo == otra.tipo;
    }
}
