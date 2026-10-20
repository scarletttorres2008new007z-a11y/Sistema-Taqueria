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

@Entity
@Table(name = "pedidos")
@Getter
@Setter
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoPedido tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    @ManyToOne(fetch = FetchType.EAGER)
    private Mesa mesa;

    private Long clienteId;
    private String clienteNombre;
    private String clienteTelefono;
    private String direccionEntrega;
    private String referenciaEntrega;
    private String notas;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false)
    private Long usuarioId;

    private String claveIdempotencia;

    @Version
    private Long version;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant actualizadoEn = Instant.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @OrderBy("id")
    private List<PedidoDetalle> detalles = new ArrayList<>();

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }

    public void agregarDetalle(PedidoDetalle d) {
        d.setPedido(this);
        detalles.add(d);
    }

    public String getNumero() {
        return id == null ? null : String.format("P-%06d", id);
    }
}
