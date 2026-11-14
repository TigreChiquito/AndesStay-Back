package cl.tigrechiquito.ms_andesstay_reservations.dto;

import static cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;

class ReservationResponseTest {

    static ReservationResponse responseIn(ReservationStatus status) {
        Reservation r = new Reservation("A", "Ana", 1L, LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12));
        ReflectionTestUtils.setField(r, "status", status);
        return ReservationResponse.from(r);
    }

    // ---------- P2-1: máquina de estados expuesta al front ----------

    @Test
    void flujoNormalYCancelacion() {
        assertThat(responseIn(CREADA).allowedTransitions()).containsExactly(CONFIRMADA, CANCELADA);
        assertThat(responseIn(CREADA).nextStatus()).isEqualTo(CONFIRMADA);
        assertThat(responseIn(CONFIRMADA).nextStatus()).isEqualTo(CHECKIN_PENDIENTE);
        assertThat(responseIn(CHECKIN_PENDIENTE).nextStatus()).isEqualTo(EN_ESTADIA);
        assertThat(responseIn(EN_ESTADIA).nextStatus()).isEqualTo(CHECKOUT);

        assertThat(responseIn(CREADA).cancellable()).isTrue();
        assertThat(responseIn(CONFIRMADA).cancellable()).isTrue();
        assertThat(responseIn(CHECKIN_PENDIENTE).cancellable()).isTrue();
        assertThat(responseIn(EN_ESTADIA).cancellable()).isFalse();
    }

    @Test
    void estadosTerminales() {
        for (ReservationStatus terminal : new ReservationStatus[] {CHECKOUT, CANCELADA}) {
            ReservationResponse r = responseIn(terminal);
            assertThat(r.allowedTransitions()).isEmpty();
            assertThat(r.nextStatus()).isNull();
            assertThat(r.cancellable()).isFalse();
        }
    }
}
