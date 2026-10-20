package com.taqueria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class TaqueriaApplication {

    public static void main(String[] args) {
        // Las fechas y horas del sistema se manejan en hora de El Salvador.
        TimeZone.setDefault(TimeZone.getTimeZone("America/El_Salvador"));
        SpringApplication.run(TaqueriaApplication.class, args);
    }
}
