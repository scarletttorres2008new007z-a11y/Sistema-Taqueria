package com.taqueria.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taqueria.repository.UsuarioRepository;
import com.taqueria.security.IdempotenciaFilter;
import com.taqueria.security.SesionVigenteFilter;
import com.taqueria.security.LimiteIntentos;
import com.taqueria.security.UsuarioSesion;
import com.taqueria.service.AuditoriaService;
import com.taqueria.web.SesionController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarios, LimiteIntentos limite) {
        return username -> {
            if (limite.bloqueado(username)) {
                throw new LockedException("Demasiados intentos fallidos. Espere 5 minutos.");
            }
            return usuarios.buscarPorUsername(username).map(UsuarioSesion::new)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UsuarioRepository usuarios, AuditoriaService auditoria,
                                           LimiteIntentos limite, ObjectMapper mapper, JdbcTemplate jdbc) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterBefore(new SesionVigenteFilter(jdbc), AuthorizationFilter.class)
            .addFilterAfter(new CsrfCookieFilter(), AuthorizationFilter.class)
            .addFilterAfter(new IdempotenciaFilter(jdbc), AuthorizationFilter.class)
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(
                    "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; " +
                    "script-src 'self'; font-src 'self'; connect-src 'self'; frame-ancestors 'self'; object-src 'none'"))
                .frameOptions(f -> f.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/configuracion-inicial.html", "/css/**", "/js/comun/**", "/js/login.js",
                        "/js/configuracion-inicial.js", "/vendor/**", "/img/**", "/archivos/logo/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/api/sesion/estado", "/api/configuracion-inicial/**").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/sesion/entrar")
                .successHandler((req, res, auth) -> {
                    UsuarioSesion u = (UsuarioSesion) auth.getPrincipal();
                    limite.exito(u.getUsername());
                    usuarios.findById(u.getId()).ifPresent(x -> {
                        x.setUltimoAcceso(Instant.now());
                        usuarios.save(x);
                    });
                    auditoria.registrar(u.getId(), u.getUsername(), "Inicio de sesión", "Usuario", u.getId(), null);
                    // Tras iniciar sesion el token CSRF cambia: se envia el nuevo en la cookie de esta respuesta
                    CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
                    if (token != null) token.getToken();
                    escribirJson(res, mapper, HttpStatus.OK, SesionController.datosSesion(u));
                })
                .failureHandler((req, res, ex) -> {
                    String usuario = req.getParameter("username");
                    String mensaje;
                    if (ex instanceof LockedException) {
                        mensaje = ex.getMessage();
                    } else if (ex instanceof org.springframework.security.authentication.DisabledException) {
                        mensaje = "El usuario está desactivado. Consulte al administrador.";
                    } else {
                        limite.fallo(usuario);
                        mensaje = "Usuario o contraseña incorrectos";
                    }
                    auditoria.registrar(null, usuario, "Inicio de sesión fallido", "Usuario", null, mensaje);
                    escribirJson(res, mapper, HttpStatus.UNAUTHORIZED, Map.of("mensaje", mensaje));
                })
                .permitAll())
            .logout(l -> l
                .logoutUrl("/api/sesion/salir")
                .addLogoutHandler((req, res, auth) -> {
                    if (auth != null && auth.getPrincipal() instanceof UsuarioSesion u) {
                        auditoria.registrar(u.getId(), u.getUsername(), "Cierre de sesión", "Usuario", u.getId(), null);
                    }
                })
                .deleteCookies("TAQUERIA_SESION")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            .exceptionHandling(e -> e
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**"))
                .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login.html"),
                        new AntPathRequestMatcher("/**"))
                .accessDeniedHandler((req, res, ex) -> escribirJson(res, mapper, HttpStatus.FORBIDDEN,
                        Map.of("mensaje", "No tiene permiso para realizar esta acción"))));
        return http.build();
    }

    static void escribirJson(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, Object cuerpo)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), cuerpo);
    }

    /** Fuerza la creacion de la cookie XSRF-TOKEN para que la interfaz pueda enviarla en cada operacion. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(req, res);
        }
    }
}
