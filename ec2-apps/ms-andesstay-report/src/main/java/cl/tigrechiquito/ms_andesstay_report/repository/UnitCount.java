package cl.tigrechiquito.ms_andesstay_report.repository;

/**
 * Proyección de Spring Data para el resultado "unidad -> cantidad de reservas".
 */
public interface UnitCount {

    Long getUnitId();

    long getTotal();
}