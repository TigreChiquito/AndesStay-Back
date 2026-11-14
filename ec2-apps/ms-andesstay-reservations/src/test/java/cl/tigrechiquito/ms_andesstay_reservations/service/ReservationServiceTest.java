package cl.tigrechiquito.ms_andesstay_reservations.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import cl.tigrechiquito.ms_andesstay_reservations.client.CatalogClient;
import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;
import cl.tigrechiquito.ms_andesstay_reservations.repository.ReservationRepository;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    ReservationRepository repository;

    @Mock
    ApplicationEventPublisher events;

    @Mock
    CatalogClient catalogClient;

    @InjectMocks
    ReservationService service;

    static Reservation reservation(long id, ReservationStatus status) {
        Reservation r = new Reservation("A", "Ana", 7L, LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12));
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "status", status);
        return r;
    }

    // ---------- P1-1: bloqueo y compensación ----------

    @Test
    void checkoutLeeLaReservaConBloqueo() {
        Reservation r = reservation(1, ReservationStatus.EN_ESTADIA);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(r));
        when(repository.saveAndFlush(any(Reservation.class))).thenReturn(r);

        service.changeStatus(1L, ReservationStatus.CHECKOUT);

        verify(repository).findByIdForUpdate(1L);
        verify(repository, never()).findById(any());
        verify(catalogClient).releaseSlot(7L);
    }

    @Test
    void siFallaElGuardadoTrasLiberarSeVuelveATomarElCupo() {
        Reservation r = reservation(1, ReservationStatus.EN_ESTADIA);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(r));
        when(repository.saveAndFlush(any(Reservation.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reservation.class, 1L));

        assertThatThrownBy(() -> service.changeStatus(1L, ReservationStatus.CHECKOUT))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        InOrder order = inOrder(catalogClient);
        order.verify(catalogClient).releaseSlot(7L);
        order.verify(catalogClient).reserveSlot(7L);
    }

    @Test
    void siFallaElGuardadoTrasReservarSeDevuelveElCupo() {
        Reservation r = reservation(1, ReservationStatus.CREADA);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(r));
        when(repository.saveAndFlush(any(Reservation.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reservation.class, 1L));

        assertThatThrownBy(() -> service.changeStatus(1L, ReservationStatus.CONFIRMADA))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        InOrder order = inOrder(catalogClient);
        order.verify(catalogClient).reserveSlot(7L);
        order.verify(catalogClient).releaseSlot(7L);
    }
}
