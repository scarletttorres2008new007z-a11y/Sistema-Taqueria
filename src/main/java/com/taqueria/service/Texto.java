package com.taqueria.service;

public final class Texto {

    private Texto() {
    }

    /** Recorta espacios y convierte cadenas vacias en null. */
    public static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
