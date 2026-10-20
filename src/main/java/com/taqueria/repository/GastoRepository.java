package com.taqueria.repository;

import com.taqueria.model.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    @Query("select g from Gasto g where g.fecha >= :desde and g.fecha <= :hasta " +
           "and (:categoriaId is null or g.categoria.id = :categoriaId) order by g.fecha desc, g.id desc")
    List<Gasto> buscar(LocalDate desde, LocalDate hasta, Long categoriaId);
}
