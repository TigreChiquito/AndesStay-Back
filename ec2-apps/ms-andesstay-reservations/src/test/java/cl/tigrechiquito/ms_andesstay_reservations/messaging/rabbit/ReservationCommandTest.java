package cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

class ReservationCommandTest {

    // ---------- P2-3: el comando a notify lleva el precio ----------

    @Test
    void elComandoLlevaLaTarifaYElTotalDeLaReserva() {
        Reservation r = new Reservation("A", "Ana", 1L, LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 13));
        r.applyPrice(new BigDecimal("45000.00"));

        ReservationCommand cmd = ReservationCommand.from(r);

        assertThat(cmd.pricePerNight()).isEqualByComparingTo("45000");
        assertThat(cmd.total()).isEqualByComparingTo("135000"); // 3 noches
    }
}
