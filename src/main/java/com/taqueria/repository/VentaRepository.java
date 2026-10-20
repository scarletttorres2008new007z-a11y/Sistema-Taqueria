package com.taqueria.repository;

import com.taqueria.model.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> bloquear(Long id);

    Optional<Venta> findByPedidoId(Long pedidoId);

    Optional<Venta> findByClaveIdempotencia(String clave);

    @Query("select v from Venta v where (:id is null or v.id = :id) and (:estado is null or v.estado = :estado) " +
           "and (:usuarioId is null or v.usuarioId = :usuarioId) and v.creadoEn >= :desde and v.creadoEn < :hasta order by v.creadoEn desc")
    Page<Venta> buscar(Long id, EstadoVenta estado, Long usuarioId, Instant desde, Instant hasta, Pageable pageable);

    List<Venta> findByCreadoEnGreaterThanEqualAndCreadoEnLessThan(Instant desde, Instant hasta);

    List<Venta> findByEstadoOrderByCreadoEnAsc(EstadoVenta estado);

    List<Venta> findByClienteIdOrderByCreadoEnDesc(Long clienteId);

    List<Venta> findByAnuladaEnGreaterThanEqualAndAnuladaEnLessThan(Instant desde, Instant hasta);
}
