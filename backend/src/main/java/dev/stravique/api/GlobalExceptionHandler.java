package dev.stravique.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException exception, HttpServletRequest request, Locale locale) {
        return error(exception.getStatus(), exception.getCode(), exception.getMessageKey(),
                Arrays.asList(exception.getArgs()), request, locale);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                     HttpServletRequest request, Locale locale) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = fieldError == null ? "" : fieldError.getField();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation", List.of(field),
                request, locale);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException exception,
                                                     HttpServletRequest request, Locale locale) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "error.validation", List.of("request"),
                request, locale);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException exception,
                                                     HttpServletRequest request, Locale locale) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", "error.invalidRequest",
                List.of(), request, locale);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request, Locale locale) {
        String requestId = requestId(request);
        log.error("Unexpected API failure requestId={} type={}", requestId, exception.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "error.internal",
                List.of(), request, locale);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String messageKey, List<Object> args,
                                           HttpServletRequest request, Locale locale) {
        String localized = messageSource.getMessage(messageKey, args.toArray(), messageKey, locale);
        ApiError error = new ApiError(code, messageKey, localized, args, requestId(request), Instant.now());
        return ResponseEntity.status(status).body(error);
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }
}
