package cl.tigrechiquito.ms_andesstay_notify.controller;

import static cl.tigrechiquito.ms_andesstay_notify.controller.RequestUser.HEADER_USER_ID;
import static cl.tigrechiquito.ms_andesstay_notify.controller.RequestUser.HEADER_USER_ROLES;
import static cl.tigrechiquito.ms_andesstay_notify.controller.RequestUser.isStaff;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_notify.domain.Voucher;
import cl.tigrechiquito.ms_andesstay_notify.service.VoucherService;

/**
 * Descarga del voucher en PDF. El huésped solo puede bajar los de sus reservas;
 * Recepcionista y Admin, cualquiera. La identidad la propaga el BFF.
 */
@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    private final VoucherService service;

    public VoucherController(VoucherService service) {
        this.service = service;
    }

    /** GET /api/vouchers/{reservationId} — PDF del voucher (404 si no existe o no es visible). */
    @GetMapping("/{reservationId}")
    public ResponseEntity<byte[]> download(
            @PathVariable Long reservationId,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLES, required = false) String roles) {

        Voucher voucher = service.findFor(reservationId, userId, isStaff(roles));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("voucher-" + voucher.getCode() + ".pdf")
                        .build()
                        .toString())
                .body(voucher.getPdf());
    }
}
