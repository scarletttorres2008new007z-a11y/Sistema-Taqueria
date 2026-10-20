package com.taqueria.repository;

import com.taqueria.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query("select c from Cliente c where (:q is null or lower(c.nombre) like lower(concat('%', cast(:q as string), '%')) " +
           "or c.telefono like concat('%', cast(:q as string), '%') or lower(coalesce(c.documento, '')) like lower(concat('%', cast(:q as string), '%'))) " +
           "and (:activo is null or c.activo = :activo) order by c.nombre")
    Page<Cliente> buscar(String q, Boolean activo, Pageable pageable);
}
