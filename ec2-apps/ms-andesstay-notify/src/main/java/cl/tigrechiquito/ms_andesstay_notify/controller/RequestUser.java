package cl.tigrechiquito.ms_andesstay_notify.controller;

import java.util.Arrays;

/**
 * Identidad que propaga el BFF desde el token validado:
 *   X-User-Id    -> oid del usuario en Azure AD
 *   X-User-Roles -> App Roles separados por coma
 */
final class RequestUser {

    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_USER_ROLES = "X-User-Roles";

    private RequestUser() {
    }

    /** Recepcionista y Admin son "personal": ven avisos STAFF y cualquier voucher. */
    static boolean isStaff(String roles) {
        if (roles == null) {
            return false;
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch(role -> role.equals("Recepcionista") || role.equals("Admin"));
    }
}
