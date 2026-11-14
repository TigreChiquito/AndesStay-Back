package cl.tigrechiquito.ms_andesstay_notify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.tigrechiquito.ms_andesstay_notify.client.CatalogClient;
import cl.tigrechiquito.ms_andesstay_notify.client.UnitInfo;
import cl.tigrechiquito.ms_andesstay_notify.domain.Voucher;
import cl.tigrechiquito.ms_andesstay_notify.messaging.CommandEnvelope;
import cl.tigrechiquito.ms_andesstay_notify.messaging.NotificationPayload;
import cl.tigrechiquito.ms_andesstay_notify.repository.VoucherRepository;
import cl.tigrechiquito.ms_andesstay_notify.service.VoucherPdfGenerator.VoucherData;

@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    VoucherRepository repository;

    @Mock
    CatalogClient catalog;

    @Mock
    VoucherPdfGenerator pdfGenerator;

    @InjectMocks
    VoucherService service;

    static CommandEnvelope<NotificationPayload> command(BigDecimal pricePerNight, BigDecimal total) {
        NotificationPayload p = new NotificationPayload(42L, "A", "Ana", 1L,
                LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12), "CONFIRMADA", pricePerNight, total);
        return new CommandEnvelope<>("voucher.gen", "e1", Instant.now(), "t1", "reservation-42", p);
    }

    private VoucherData generatedData() {
        ArgumentCaptor<VoucherData> captor = ArgumentCaptor.forClass(VoucherData.class);
        verify(pdfGenerator).generate(captor.capture());
        return captor.getValue();
    }

    // ---------- P2-3: el voucher usa el precio guardado en la reserva ----------

    @Test
    void usaElPrecioDelComandoAunqueCatalogHayaCambiadoLaTarifa() {
        when(catalog.findUnit(1L)).thenReturn(Optional.of(
                new UnitInfo(1L, "Cabaña Sur", "CABANA", "Cajón del Maipo", new BigDecimal("99000"))));
        when(pdfGenerator.generate(any())).thenReturn(new byte[] {1});

        service.generate(command(new BigDecimal("50000"), new BigDecimal("100000")));

        VoucherData data = generatedData();
        assertThat(data.pricePerNight()).isEqualByComparingTo("50000");
        assertThat(data.total()).isEqualByComparingTo("100000");
        assertThat(data.unitName()).isEqualTo("Cabaña Sur");

        ArgumentCaptor<Voucher> saved = ArgumentCaptor.forClass(Voucher.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTotal()).isEqualByComparingTo("100000");
    }

    @Test
    void reservaAntiguaSinPrecioUsaLaTarifaDeCatalog() {
        when(catalog.findUnit(1L)).thenReturn(Optional.of(
                new UnitInfo(1L, "Cabaña Sur", "CABANA", "Cajón del Maipo", new BigDecimal("99000"))));
        when(pdfGenerator.generate(any())).thenReturn(new byte[] {1});

        service.generate(command(null, null));

        VoucherData data = generatedData();
        assertThat(data.pricePerNight()).isEqualByComparingTo("99000");
        assertThat(data.total()).isEqualByComparingTo("198000");
    }
}
