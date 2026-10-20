package com.ms_tallerpro.notify.controller;

import com.ms_tallerpro.notify.model.Notificacion;
import com.ms_tallerpro.notify.service.NotificacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Endpoint INTERNO de observabilidad (no se rutea en el gateway; caso seccion 5:
 * notify "no publico"). Sirve para evidenciar en la defensa que los comandos llegaron
 * y fueron procesados. Solo Admin.
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Ultimas notificaciones procesadas (en memoria)")
public class NotificacionController {

    private final NotificacionService service;

    @GetMapping
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Ultimas notificaciones procesadas, mas reciente primero")
    public List<Notificacion> recientes(@RequestParam(defaultValue = "50") int limite) {
        return service.recientes(Math.min(limite, 500));
    }

    @GetMapping("/{eventId}/pdf")
    @PreAuthorize("hasRole('Admin')")
    @Operation(summary = "Descarga el PDF generado para un comando de q.cmd.quote (presupuesto/orden de trabajo)")
    public ResponseEntity<byte[]> pdf(@PathVariable String eventId) {
        Optional<Notificacion> n = service.porEventId(eventId);
        if (n.isEmpty() || n.get().pdf() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + eventId + ".pdf\"")
                .body(n.get().pdf());
    }
}
