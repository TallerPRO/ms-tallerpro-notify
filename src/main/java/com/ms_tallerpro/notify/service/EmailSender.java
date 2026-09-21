package com.ms_tallerpro.notify.service;

/** Puerto de salida para correo. Implementaciones: SMTP real o registro en log. */
public interface EmailSender {

    /** @return descripcion corta del resultado (para el registro de la notificacion). */
    String enviar(String destinatario, String asunto, String cuerpo);
}
