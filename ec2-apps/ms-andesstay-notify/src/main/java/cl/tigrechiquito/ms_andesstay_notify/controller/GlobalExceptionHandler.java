package cl.tigrechiquito.ms_andesstay_notify.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.tigrechiquito.ms_andesstay_notify.domain.NotificationNotFoundException;

/** Traduce las excepciones de dominio a ProblemDetail (RFC 7807). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ProblemDetail handleNotFound(NotificationNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
