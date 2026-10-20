package com.taqueria.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Redondeo monetario uniforme (2 decimales, mitad hacia arriba). */
public final class Dinero {

    public static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private Dinero() {
    }

    public static BigDecimal r(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** IVA contenido en un total que ya lo incluye: total - total / (1 + tasa). */
    public static BigDecimal ivaIncluido(BigDecimal total, BigDecimal porcentaje) {
        if (porcentaje == null || porcentaje.signum() == 0) return r(BigDecimal.ZERO);
        BigDecimal divisor = BigDecimal.ONE.add(porcentaje.divide(CIEN, 6, RoundingMode.HALF_UP));
        BigDecimal base = total.divide(divisor, 2, RoundingMode.HALF_UP);
        return r(total.subtract(base));
    }
}
