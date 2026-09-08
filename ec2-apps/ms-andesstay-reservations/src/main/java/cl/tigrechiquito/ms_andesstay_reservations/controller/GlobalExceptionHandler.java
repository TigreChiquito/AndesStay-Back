package cl.tigrechiquito.ms_andesstay_reservations.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.tigrechiquito.ms_andesstay_reservations.domain.InvalidReservationStatusTransitionException;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationNotFoundException;

/**
 * Traduce las excepciones del dominio/validación a respuestas HTTP usando
 * ProblemDetail (RFC 7807), el formato estándar de errores en Spring 6+/Boot 4.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Reserva inexistente -> 404. */
    @ExceptionHandler(ReservationNotFoundException.class)
    public ProblemDetail handleNotFound(ReservationNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Transición de estado no permitida -> 409 Conflict. */
    @ExceptionHandler(InvalidReservationStatusTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidReservationStatusTransitionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setProperty("from", ex.getFrom());
        problem.setProperty("to", ex.getTo());
        return problem;
    }

    /** Reglas de negocio simples (ej. checkOut <= checkIn) -> 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Fallos de @Valid en el body -> 400 con el detalle por campo. */
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

    /** JSON ilegible o valor de enum inválido (ej. un status inexistente) -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON inválido o valor no permitido (revisa el campo 'status').");
    }
}