package com.taqueria.service;

import com.taqueria.model.UnidadMedida;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Conversiones controladas entre unidades del mismo tipo (masa, volumen o unidades). */
@Service
public class UnidadService {

    public BigDecimal convertir(BigDecimal cantidad, UnidadMedida desde, UnidadMedida hacia) {
        if (desde.getId().equals(hacia.getId())) return cantidad;
        if (!desde.esCompatibleCon(hacia)) {
            throw new ApiException("No se puede convertir " + desde.getNombre() + " a " + hacia.getNombre()
                    + ": son unidades de distinto tipo");
        }
        return cantidad.multiply(desde.getFactor()).divide(hacia.getFactor(), 6, RoundingMode.HALF_UP);
    }
}
