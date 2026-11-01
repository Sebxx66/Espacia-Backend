package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

// Varias personas intentan reservar el mismo espacio y horario en el mismo instante
class ConcurrencyApiTests extends ApiTestBase {

    private static final int INTENTOS = 8;

    @Test
    void soloUnaDeVariasReservasSimultaneasDelMismoHorarioSeGuarda() throws Exception {
        long espacio = newSpace();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < INTENTOS; i++) tokens.add(newStudent());

        List<Integer> estados = enParalelo(tokens, token -> reserve(token, espacio, tomorrow(), "10:00", "12:00").status());

        assertThat(estados).filteredOn(s -> s == 200).hasSize(1);
        assertThat(estados).filteredOn(s -> s == 409).hasSize(INTENTOS - 1);
        String admin = adminToken();
        long guardadas = StreamSupport.stream(get("/reservations?fecha=" + tomorrow(), admin).body().spliterator(), false)
                .filter(r -> r.get("espacioId").asLong() == espacio)
                .count();
        assertThat(guardadas).isEqualTo(1);
    }

    @Test
    void unUsuarioNoSuperaElLimiteDe3EnviandoVariasReservasALaVez() throws Exception {
        String token = newStudent();
        List<Long> espacios = new ArrayList<>();
        for (int i = 0; i < 6; i++) espacios.add(newSpace());
        String[][] bloques = {{"08:00", "09:00"}, {"09:00", "10:00"}, {"10:00", "11:00"},
                {"11:00", "12:00"}, {"12:00", "13:00"}, {"13:00", "14:00"}};

        // Cada reserva va en un día distinto para que el límite diario (2 salas por día) no interfiera
        List<Integer> indices = List.of(0, 1, 2, 3, 4, 5);
        List<Integer> estados = enParalelo(indices,
                i -> reserve(token, espacios.get(i), LocalDate.now().plusDays(i + 1), bloques[i][0], bloques[i][1]).status());

        assertThat(estados).filteredOn(s -> s == 200).hasSize(3);
        assertThat(get("/reservations/me", token).body().size()).isEqualTo(3);
    }

    private <T> List<Integer> enParalelo(List<T> items, java.util.function.Function<T, Integer> accion) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(items.size());
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Integer>> futuros = new ArrayList<>();
            for (T item : items) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    return accion.apply(item);
                }));
            }
            salida.countDown();
            List<Integer> resultados = new ArrayList<>();
            for (Future<Integer> f : futuros) resultados.add(f.get());
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
