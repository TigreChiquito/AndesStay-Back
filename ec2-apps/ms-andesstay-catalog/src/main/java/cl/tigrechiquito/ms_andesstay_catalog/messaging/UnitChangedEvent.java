package cl.tigrechiquito.ms_andesstay_catalog.messaging;

import cl.tigrechiquito.ms_andesstay_catalog.domain.Unit;

/** Evento interno de Spring: la unidad cambió. Se publica a Kafka tras el commit. */
public record UnitChangedEvent(Unit unit) {
}
