package cl.tigrechiquito.ms_andesstay_report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import cl.tigrechiquito.ms_andesstay_report.domain.UnitState;
import cl.tigrechiquito.ms_andesstay_report.messaging.UnitStateMessage;
import cl.tigrechiquito.ms_andesstay_report.repository.ReservationProjectionRepository;
import cl.tigrechiquito.ms_andesstay_report.repository.UnitStateRepository;

class ProjectionServiceTest {

    private final UnitStateRepository units = mock(UnitStateRepository.class);
    private final ProjectionService service =
            new ProjectionService(mock(ReservationProjectionRepository.class), units);

    private static UnitStateMessage state(int total, int available, Instant at) {
        return new UnitStateMessage(1L, "Cabaña", "CABANA", "Pirque", total, available,
                BigDecimal.TEN, true, at);
    }

    @Test
    void aplicaElEstadoDeLaUnidad() {
        when(units.findById(1L)).thenReturn(Optional.empty());
        service.applyUnitState(state(4, 3, Instant.now()));
        verify(units).save(any(UnitState.class));
    }

    @Test
    void ignoraUnEstadoMasViejoQueElAplicado() {
        UnitState current = new UnitState(1L);
        current.apply("Cabaña", "CABANA", 4, 2, BigDecimal.TEN, true, Instant.parse("2026-11-21T12:00:00Z"));
        when(units.findById(1L)).thenReturn(Optional.of(current));

        service.applyUnitState(state(4, 4, Instant.parse("2026-11-21T11:00:00Z")));

        verify(units, never()).save(any());
        assertThat(current.getAvailableSlots()).isEqualTo(2);
    }

    @Test
    void rechazaCuposImposiblesSinTocarLaBase() {
        assertThatThrownBy(() -> service.applyUnitState(state(2, 5, Instant.now())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(units, never()).findById(any());
    }
}
