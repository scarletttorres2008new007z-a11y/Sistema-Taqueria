package com.taqueria.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ingrediente, producto terminado o material controlado en inventario. */
@Entity
@Table(name = "inventario_items")
@Getter
@Setter
@NoArgsConstructor
public class InventarioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoItem tipo;

    @Column(nullable = false, length = 40)
    private String categoria;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private UnidadMedida unidad;

    /** Solo se modifica mediante movimientos de inventario (InventarioService). */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal existencia = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal existenciaMinima = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal costoUnitario = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.EAGER)
    private Proveedor proveedor;

    private LocalDate fechaVencimiento;

    private String lote;

    private boolean activo = true;

    private boolean demo;

    @Version
    private Long version;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant actualizadoEn = Instant.now();

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }

    /** AGOTADO, BAJO u OK. */
    public String getEstadoExistencia() {
        if (existencia.signum() <= 0) return "AGOTADO";
        if (existencia.compareTo(existenciaMinima) <= 0) return "BAJO";
        return "OK";
    }

    /** VENCIDO, POR_VENCER (proximos 3 dias) o null. */
    public String getEstadoVencimiento() {
        if (fechaVencimiento == null) return null;
        LocalDate hoy = LocalDate.now();
        if (fechaVencimiento.isBefore(hoy)) return "VENCIDO";
        if (!fechaVencimiento.isAfter(hoy.plusDays(3))) return "POR_VENCER";
        return null;
    }
}
