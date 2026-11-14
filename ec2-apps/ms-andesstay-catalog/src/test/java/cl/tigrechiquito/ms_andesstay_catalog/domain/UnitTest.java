package cl.tigrechiquito.ms_andesstay_catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class UnitTest {

    static Unit unit(int totalSlots) {
        return new Unit("Cabaña Sur", UnitType.CABANA, "Cajón del Maipo", totalSlots, new BigDecimal("50000"));
    }

    // ---------- P1-2: unidad inactiva ----------

    @Test
    void unaUnidadInactivaNoSePuedeReservar() {
        Unit u = unit(2);
        u.setActive(false);

        assertThatThrownBy(u::reserveOne).isInstanceOf(UnitInactiveException.class);
        assertThat(u.getAvailableSlots()).isEqualTo(2);
    }

    @Test
    void unaUnidadInactivaIgualRecibeLosCuposDevueltos() {
        Unit u = unit(2);
        u.reserveOne();
        u.setActive(false);

        u.releaseOne();

        assertThat(u.getAvailableSlots()).isEqualTo(2);
    }

    // ---------- P1-5: cupo total vs. cupos ocupados ----------

    @Test
    void elTotalNoPuedeQuedarBajoLosCuposOcupados() {
        Unit u = unit(5);
        u.reserveOne();
        u.reserveOne();
        u.reserveOne(); // 3 ocupados

        assertThatThrownBy(() -> u.adjustTotalSlots(2))
                .isInstanceOf(SlotsInUseException.class)
                .hasMessage("Hay 3 cupos ocupados; el total no puede ser menor.");
        assertThat(u.getTotalSlots()).isEqualTo(5);
        assertThat(u.getAvailableSlots()).isEqualTo(2);
    }

    @Test
    void bajarElTotalHastaLosOcupadosDejaCeroDisponibles() {
        Unit u = unit(5);
        u.reserveOne();
        u.reserveOne();

        u.adjustTotalSlots(2);

        assertThat(u.getTotalSlots()).isEqualTo(2);
        assertThat(u.getAvailableSlots()).isZero();
    }
}
