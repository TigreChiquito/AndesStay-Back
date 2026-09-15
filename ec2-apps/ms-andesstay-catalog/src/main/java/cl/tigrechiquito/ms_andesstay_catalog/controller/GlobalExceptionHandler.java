package cl.tigrechiquito.ms_andesstay_catalog.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.tigrechiquito.ms_andesstay_catalog.domain.NoAvailabilityException;
import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Unidad inexistente -> 404. */
    @ExceptionHandler(UnitNotFoundException.class)
    public ProblemDetail handleNotFound(UnitNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Sin cupos disponibles -> 409 Conflict. */
    @ExceptionHandler(NoAvailabilityException.class)
    public ProblemDetail handleNoAvailability(NoAvailabilityException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Choque de concurrencia (dos confirmaciones sobre el último cupo) -> 409.
     * El @Version de Unit dispara esto; el cliente debería reintentar.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "La unidad fue modificada por otra operación. Reintenta.");
    }

    /** Reglas de negocio simples -> 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Fallos de @Valid en el body -> 400 con detalle por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Payload inválido");

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fieldError -> errors.put(fieldError.getField(), fieldError.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    /** JSON ilegible o valor de enum inválido (ej. un type inexistente) -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON inválido o valor no permitido (revisa el campo 'type').");
    }
}