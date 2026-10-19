package cl.tigrechiquito.ms_andesstay_rabbit_admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Lo que el controller rechaza con 400 antes de tocar RabbitMQ. */
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private static <T> Set<String> invalidFields(T request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void colaValida() {
        assertThat(invalidFields(new CreateQueueRequest("q.demo-1_x", true, true, 1000L, 10, "drop-head"))).isEmpty();
    }

    @Test
    void colaConNombreVacioOInvalido() {
        assertThat(invalidFields(new CreateQueueRequest("", null, false, null, null, null))).contains("name");
        assertThat(invalidFields(new CreateQueueRequest("   ", null, false, null, null, null))).contains("name");
        assertThat(invalidFields(new CreateQueueRequest("cola con espacios", null, false, null, null, null))).contains("name");
        assertThat(invalidFields(new CreateQueueRequest("q/demo", null, false, null, null, null))).contains("name");
    }

    @Test
    void colaConRetencionInvalida() {
        assertThat(invalidFields(new CreateQueueRequest("q.demo", null, false, 0L, -5, "borrar-todo")))
                .containsExactlyInAnyOrder("messageTtlMs", "maxLength", "overflow");
    }

    @Test
    void exchangeConTipoInvalido() {
        assertThat(invalidFields(new CreateExchangeRequest("ex.demo", "headers", null))).contains("type");
        assertThat(invalidFields(new CreateExchangeRequest("ex.demo", "", null))).contains("type");
        assertThat(invalidFields(new CreateExchangeRequest("ex.demo", "topic", null))).isEmpty();
    }

    @Test
    void bindingInvalido() {
        assertThat(invalidFields(new BindingRequest("", "q.demo", "a.b"))).contains("exchange");
        assertThat(invalidFields(new BindingRequest("ex.demo", "q.demo", null))).contains("routingKey");
        assertThat(invalidFields(new BindingRequest("ex.demo", "q.demo", "reserva creada"))).contains("routingKey");
        assertThat(invalidFields(new BindingRequest("ex.demo", "q.demo", "reserva.*.#"))).isEmpty();
        assertThat(invalidFields(new BindingRequest("ex.demo", "q.demo", ""))).isEmpty();
    }
}
