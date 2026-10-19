package cl.tigrechiquito.ms_andesstay_rabbit_admin.dto;

/** Regla común para nombres de colas y exchanges. */
final class NamePatterns {

    static final String NAME = "[A-Za-z0-9._-]+";
    static final String NAME_MESSAGE = "solo se admiten letras, números, '.', '_' y '-'";

    private NamePatterns() {
    }
}
