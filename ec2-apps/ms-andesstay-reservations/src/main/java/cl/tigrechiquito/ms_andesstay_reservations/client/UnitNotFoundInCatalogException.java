package cl.tigrechiquito.ms_andesstay_reservations.client;

/**
 * catalog respondió 404: la unidad no existe. Es un IllegalArgumentException
 * para que siga mapeándose a HTTP 400 hacia el cliente.
 */
public class UnitNotFoundInCatalogException extends IllegalArgumentException {

    public UnitNotFoundInCatalogException(Long unitId) {
        super("La unidad " + unitId + " no existe en el catálogo");
    }
}
