package com.taqueria.repository;

import com.taqueria.model.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> bloquear(Long id);

    Optional<Pedido> findByClaveIdempotencia(String clave);

    List<Pedido> findByEstadoInOrderByCreadoEnAsc(Collection<EstadoPedido> estados);

    @Query("select p from Pedido p where (:id is null or p.id = :id) and (:estado is null or p.estado = :estado) " +
           "and (:tipo is null or p.tipo = :tipo) and p.creadoEn >= :desde and p.creadoEn < :hasta order by p.creadoEn desc")
    Page<Pedido> buscar(Long id, EstadoPedido estado, TipoPedido tipo, Instant desde, Instant hasta, Pageable pageable);

    List<Pedido> findByCreadoEnBetween(Instant desde, Instant hasta);

    List<Pedido> findByClienteIdOrderByCreadoEnDesc(Long clienteId);
}
