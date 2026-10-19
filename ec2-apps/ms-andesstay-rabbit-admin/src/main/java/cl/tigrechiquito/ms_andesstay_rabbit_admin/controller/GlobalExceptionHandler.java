package cl.tigrechiquito.ms_andesstay_rabbit_admin.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.amqp.AmqpConnectException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ManagementUnavailableException;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ResourceConflictException;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ResourceNotFoundException;

/** Traduce los errores a ProblemDetail (RFC 7807) con mensajes claros. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Validación de los DTOs: responde qué campo falló y por qué. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Configuración inválida");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        String reason = cause != null ? cause.lines().findFirst().orElse("") : "";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON inválido o con tipos incorrectos: " + reason);
    }

    /** Reglas que dependen del estado del broker (ej. binding a una cola inexistente). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Ya existe o es un recurso protegido de la topología del sistema. */
    @ExceptionHandler(ResourceConflictException.class)
    public ProblemDetail handleConflict(ResourceConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ManagementUnavailableException.class)
    public ProblemDetail handleManagementDown(ManagementUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(AmqpConnectException.class)
    public ProblemDetail handleBrokerDown(AmqpConnectException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo conectar a RabbitMQ por AMQP: " + ex.getMessage());
    }
}
