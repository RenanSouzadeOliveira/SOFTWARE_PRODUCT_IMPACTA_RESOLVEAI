package br.edu.impacta.resolveai.shared.error;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Dados invalidos", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Dados invalidos", request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = invalidFormatViolation(exception);
        return build(HttpStatus.BAD_REQUEST, "Corpo da requisicao invalido", request, violations);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Parametros da requisicao invalidos", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Recurso nao encontrado", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Metodo HTTP nao permitido", request, List.of());
    }

    @ExceptionHandler(NaoAutorizadoException.class)
    ResponseEntity<ApiErrorResponse> handleUnauthorized(
            NaoAutorizadoException exception,
            HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflitoException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(
            ConflitoException exception,
            HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(DadosInvalidosException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidData(
            DadosInvalidosException exception,
            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", request, List.of());
    }

    private FieldViolation toViolation(FieldError error) {
        String message = error.getDefaultMessage() == null ? "valor invalido" : error.getDefaultMessage();
        return new FieldViolation(error.getField(), message);
    }

    private List<FieldViolation> invalidFormatViolation(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (!(cause instanceof InvalidFormatException invalidFormat)) {
            return List.of();
        }

        String field = invalidFormat.getPath().stream()
                .map(reference -> reference.getFieldName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
        if (field.isBlank()) {
            return List.of();
        }
        return List.of(new FieldViolation(field, "valor invalido"));
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                violations);
        return ResponseEntity.status(status).body(body);
    }
}
