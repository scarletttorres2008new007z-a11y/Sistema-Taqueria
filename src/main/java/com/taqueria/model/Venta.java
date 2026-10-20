package com.taqueria.model;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Operacion comercial registrada. Se distingue del pedido (lo que se prepara) y del pago. */
@Entity
@Table(name = "ventas")
@Getter
@Setter
@NoArgsConstructor
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Pedido de origen; null en una venta directa de mostrador. */
    private Long pedidoId;

    private Long clienteId;
    private String clienteNombre;
    private String clienteDocumento;
    private String clienteDireccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVenta estado;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    /** Total sin IVA. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    /** Tasa de IVA vigente al registrar la venta. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal ivaPorcentaje;

    /** IVA incluido en el total. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal impuesto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    /** Costo estimado de preparacion (para calcular la utilidad). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costoEstimado = BigDecimal.ZERO;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant anuladaEn;
    private Long anuladaPor;
    private String motivoAnulacion;
    private boolean inventarioDevuelto;

    private String claveIdempotencia;

    @Version
    private Long version;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("id")
    private List<VentaDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(VentaDetalle d) {
        d.setVenta(this);
        detalles.add(d);
    }

    public String getNumero() {
        return id == null ? null : String.format("V-%06d", id);
    }

    public BigDecimal getSaldo() {
        return total.subtract(montoPagado);
    }
}
