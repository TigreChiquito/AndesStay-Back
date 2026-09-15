package cl.tigrechiquito.ms_andesstay_report.repository;

/**
 * Proyección de Spring Data para el resultado "estado -> cantidad".
 * Los nombres de los getters calzan con los alias del @Query (as status, as total).
 */
public interface StatusCount {

    String getStatus();

    long getTotal();
}