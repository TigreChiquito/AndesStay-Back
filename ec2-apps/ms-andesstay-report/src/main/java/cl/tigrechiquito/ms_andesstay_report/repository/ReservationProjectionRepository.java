package cl.tigrechiquito.ms_andesstay_report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import cl.tigrechiquito.ms_andesstay_report.domain.ReservationProjection;

/**
 * Acceso a datos del read model + agregaciones para los KPIs.
 * Las agregaciones se resuelven en la base (GROUP BY), no en memoria.
 */
public interface ReservationProjectionRepository extends JpaRepository<ReservationProjection, Long> {

    @Query("""
            select p.status as status, count(p) as total
            from ReservationProjection p
            group by p.status
            order by p.status
            """)
    List<StatusCount> countGroupedByStatus();

    @Query("""
            select p.unitId as unitId, count(p) as total
            from ReservationProjection p
            group by p.unitId
            order by count(p) desc
            """)
    List<UnitCount> countGroupedByUnit();
}