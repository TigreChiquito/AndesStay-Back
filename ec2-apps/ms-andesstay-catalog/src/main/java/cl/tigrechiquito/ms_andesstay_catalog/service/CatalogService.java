package cl.tigrechiquito.ms_andesstay_catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_catalog.domain.Unit;
import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitNotFoundException;
import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitType;
import cl.tigrechiquito.ms_andesstay_catalog.dto.CreateUnitRequest;
import cl.tigrechiquito.ms_andesstay_catalog.dto.UpdateUnitRequest;
import cl.tigrechiquito.ms_andesstay_catalog.repository.UnitRepository;

@Service
public class CatalogService {

    private final UnitRepository repository;

    public CatalogService(UnitRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Unit create(CreateUnitRequest req) {
        Unit unit = new Unit(
                req.name(),
                req.type(),
                req.location(),
                req.totalSlots(),
                req.pricePerNight());
        return repository.save(unit);
    }

    @Transactional(readOnly = true)
    public Unit getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new UnitNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Unit> search(UnitType type, boolean onlyAvailable) {
        if (onlyAvailable) {
            List<Unit> available = repository.findByActiveTrueAndAvailableSlotsGreaterThan(0);
            if (type != null) {
                return available.stream().filter(u -> u.getType() == type).toList();
            }
            return available;
        }
        if (type != null) {
            return repository.findByType(type);
        }
        return repository.findAll();
    }

    @Transactional
    public Unit update(Long id, UpdateUnitRequest req) {
        Unit unit = getById(id);
        unit.setName(req.name());
        unit.setType(req.type());
        unit.setLocation(req.location());
        unit.setPricePerNight(req.pricePerNight());
        unit.setActive(req.active());
        unit.adjustTotalSlots(req.totalSlots()); // reajusta disponibilidad
        return repository.save(unit);
    }

    @Transactional
    public void delete(Long id) {
        Unit unit = getById(id); // 404 si no existe
        repository.delete(unit);
    }

    /** Descuenta un cupo. Lo llama reservations al CONFIRMAR una reserva. */
    @Transactional
    public Unit reserveOne(Long id) {
        Unit unit = getById(id);
        unit.reserveOne(); // lanza NoAvailabilityException si no hay cupos
        return repository.save(unit);
    }

    /** Devuelve un cupo. Lo llama reservations al CANCELAR una reserva. */
    @Transactional
    public Unit releaseOne(Long id) {
        Unit unit = getById(id);
        unit.releaseOne();
        return repository.save(unit);
    }
}