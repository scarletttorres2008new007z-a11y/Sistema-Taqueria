package com.taqueria.repository;

import com.taqueria.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findAllByOrderByCategoriaOrdenAscNombreAsc();

    boolean existsByCodigoIgnoreCase(String codigo);

    @Query("select coalesce(max(p.id), 0) from Producto p")
    long maxId();
}
