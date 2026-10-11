package cl.tigrechiquito.ms_andesstay_notify.service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_notify.client.CatalogClient;
import cl.tigrechiquito.ms_andesstay_notify.client.UnitInfo;
import cl.tigrechiquito.ms_andesstay_notify.domain.Voucher;
import cl.tigrechiquito.ms_andesstay_notify.domain.VoucherNotFoundException;
import cl.tigrechiquito.ms_andesstay_notify.messaging.CommandEnvelope;
import cl.tigrechiquito.ms_andesstay_notify.messaging.NotificationPayload;
import cl.tigrechiquito.ms_andesstay_notify.repository.VoucherRepository;
import cl.tigrechiquito.ms_andesstay_notify.service.VoucherPdfGenerator.VoucherData;

/**
 * Emite el voucher al recibir voucher.gen (reserva CONFIRMADA) y lo entrega por API.
 */
@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);

    private final VoucherRepository repository;
    private final CatalogClient catalog;
    private final VoucherPdfGenerator pdfGenerator;

    public VoucherService(VoucherRepository repository, CatalogClient catalog,
                          VoucherPdfGenerator pdfGenerator) {
        this.repository = repository;
        this.catalog = catalog;
        this.pdfGenerator = pdfGenerator;
    }

    /** Genera y guarda el voucher. Idempotente: si la reserva ya tiene uno, no hace nada. */
    @Transactional
    public void generate(CommandEnvelope<NotificationPayload> env) {
        NotificationPayload p = env.payload();
        if (repository.existsByReservationId(p.reservationId())) {
            log.info("La reserva {} ya tiene voucher, se ignora", p.reservationId());
            return;
        }

        Optional<UnitInfo> unit = catalog.findUnit(p.unitId());
        long nights = (p.checkInDate() != null && p.checkOutDate() != null)
                ? ChronoUnit.DAYS.between(p.checkInDate(), p.checkOutDate())
                : 0;
        BigDecimal pricePerNight = unit.map(UnitInfo::pricePerNight).orElse(null);
        BigDecimal total = (pricePerNight != null && nights > 0)
                ? pricePerNight.multiply(BigDecimal.valueOf(nights))
                : null;
        String code = "AS-%06d".formatted(p.reservationId());

        byte[] pdf = pdfGenerator.generate(new VoucherData(
                code,
                p.reservationId(),
                p.guestName(),
                p.unitId(),
                unit.map(UnitInfo::name).orElse(null),
                unit.map(UnitInfo::location).orElse(null),
                p.checkInDate(),
                p.checkOutDate(),
                nights,
                pricePerNight,
                total));

        repository.save(new Voucher(p.reservationId(), code, p.guestId(), p.guestName(), p.unitId(),
                p.checkInDate(), p.checkOutDate(), total, pdf));
        log.info("VOUCHER {} emitido para la reserva {} ({} bytes)", code, p.reservationId(), pdf.length);
    }

    /** Devuelve el voucher si es visible para el usuario; si no, 404. */
    @Transactional(readOnly = true)
    public Voucher findFor(Long reservationId, String userId, boolean staff) {
        return repository.findByReservationId(reservationId)
                .filter(v -> v.isVisibleTo(userId, staff))
                .orElseThrow(() -> new VoucherNotFoundException(reservationId));
    }
}
