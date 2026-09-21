package com.ms_tallerpro.notify.repository;

import com.ms_tallerpro.notify.model.Notificacion;
import org.springframework.stereotype.Repository;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Almacen en memoria (sin BD, por diseno): ultimos N registros + indice por eventId
 * para descartar redeliveries de RabbitMQ. Se pierde al reiniciar; aceptable porque
 * las colas son durables y la idempotencia real vive en el eventId del productor.
 */
@Repository
public class NotificacionRepository {

    private static final int CAPACIDAD = 1000;

    private final Deque<Notificacion> recientes = new ArrayDeque<>();
    private final Map<String, Notificacion> porEventId = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Notificacion> eldest) {
            return size() > CAPACIDAD;
        }
    };

    public synchronized Notificacion guardar(Notificacion n) {
        porEventId.put(n.eventId(), n);
        recientes.addFirst(n);
        if (recientes.size() > CAPACIDAD) {
            recientes.removeLast();
        }
        return n;
    }

    public synchronized Optional<Notificacion> porEventId(String eventId) {
        return Optional.ofNullable(porEventId.get(eventId));
    }

    public synchronized boolean yaProcesado(String eventId) {
        return porEventId.containsKey(eventId);
    }

    public synchronized List<Notificacion> recientes(int limite) {
        List<Notificacion> lista = new ArrayList<>();
        for (Notificacion n : recientes) {
            if (lista.size() >= limite) break;
            lista.add(n);
        }
        return lista;
    }

    public synchronized long total() {
        return recientes.size();
    }
}
