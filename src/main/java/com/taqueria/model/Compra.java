package com.taqueria.model;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "compras")
@Getter
@Setter
@NoArgsConstructor
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Proveedor proveedor;

    @Column(nullable = false)
    private LocalDate fecha;

    private String numeroFactura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCompra estado = EstadoCompra.PENDIENTE_RECEPCION;

    private Instant fechaRecepcion;

    private Long recibidoPor;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    private LocalDate fechaVencimientoPago;

    private String notas;

    @Column(nullable = false)
    private Long usuarioId;

    private boolean demo;

    @Version
    private Long version;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("id")
    private List<CompraDetalle> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("id")
    private List<PagoCompra> pagos = new ArrayList<>();

    public String getNumero() {
        return id == null ? null : String.format("C-%06d", id);
    }

    public BigDecimal getSaldo() {
        return total.subtract(montoPagado);
    }

    /** PAGADA, PARCIAL o PENDIENTE. */
    public String getEstadoPago() {
        if (getSaldo().signum() <= 0) return "PAGADA";
        return montoPagado.signum() > 0 ? "PARCIAL" : "PENDIENTE";
    }

    public boolean isVencida() {
        return estado != EstadoCompra.ANULADA && getSaldo().signum() > 0 && fechaVencimientoPago != null
                && fechaVencimientoPago.isBefore(LocalDate.now());
    }
}
