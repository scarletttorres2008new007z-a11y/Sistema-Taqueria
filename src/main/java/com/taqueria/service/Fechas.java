package com.taqueria.service;

import java.time.*;

/** Fechas del negocio en hora de El Salvador. */
public final class Fechas {

    public static final ZoneId ZONA = ZoneId.of("America/El_Salvador");

    private Fechas() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    public static Instant inicio(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA).toInstant();
    }

    /** Inicio del dia siguiente (limite exclusivo). */
    public static Instant finExclusivo(LocalDate fecha) {
        return fecha.plusDays(1).atStartOfDay(ZONA).toInstant();
    }

    public static LocalDate fecha(Instant instante) {
        return instante.atZone(ZONA).toLocalDate();
    }

    public static LocalDate o(LocalDate fecha, LocalDate porDefecto) {
        return fecha == null ? porDefecto : fecha;
    }

    public static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) throw new ApiException("La fecha inicial no puede ser posterior a la final");
        if (desde.plusYears(3).isBefore(hasta)) throw new ApiException("El rango de fechas no puede superar 3 años");
    }
}
