package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    private String descripcion;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProducto tipo;

    /** Precio de venta con IVA incluido. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costoEstimado = BigDecimal.ZERO;

    private String imagenUrl;

    /** Para productos de reventa: item de inventario que se descuenta por unidad vendida. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_inventario_id")
    private InventarioItem itemInventario;

    private boolean activo = true;

    private boolean disponible = true;

    private boolean demo;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant actualizadoEn = Instant.now();

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }
}
