package com.taqueria.repository;

import com.taqueria.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {
}
