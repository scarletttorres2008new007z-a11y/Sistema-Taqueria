package com.taqueria.repository;

import com.taqueria.model.*;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InventarioItemRepository extends JpaRepository<InventarioItem, Long> {

    List<InventarioItem> findAllByOrderByNombreAsc();

    boolean existsByCodigoIgnoreCase(String codigo);

    /** Bloquea la fila para actualizar la existencia sin carreras entre operaciones simultaneas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventarioItem i where i.id = :id")
    Optional<InventarioItem> bloquear(Long id);

    @Query("select coalesce(max(i.id), 0) from InventarioItem i")
    long maxId();
}
