package com.taqueria.repository;

import com.taqueria.model.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> bloquear(Long id);

    @Query("select c from Compra c where c.fecha >= :desde and c.fecha <= :hasta " +
           "and (:proveedorId is null or c.proveedor.id = :proveedorId) order by c.fecha desc, c.id desc")
    List<Compra> buscar(LocalDate desde, LocalDate hasta, Long proveedorId);

    @Query("select c from Compra c where c.estado <> com.taqueria.model.EstadoCompra.ANULADA and c.montoPagado < c.total order by c.fechaVencimientoPago nulls last")
    List<Compra> conSaldo();
}
