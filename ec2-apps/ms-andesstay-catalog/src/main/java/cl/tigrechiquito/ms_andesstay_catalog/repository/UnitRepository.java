package cl.tigrechiquito.ms_andesstay_catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.tigrechiquito.ms_andesstay_catalog.domain.Unit;
import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitType;

/**
 * Acceso a datos del catálogo de unidades.
 */
public interface UnitRepository extends JpaRepository<Unit, Long> {

    List<Unit> findByType(UnitType type);

    List<Unit> findByActiveTrue();

    /** Unidades activas con al menos un cupo disponible. */
    List<Unit> findByActiveTrueAndAvailableSlotsGreaterThan(int minSlots);
}