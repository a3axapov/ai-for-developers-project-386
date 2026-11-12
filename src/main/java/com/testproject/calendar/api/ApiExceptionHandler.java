package com.testproject.calendar.api;

import com.testproject.calendar.generated.model.ApiError;
import com.testproject.calendar.owner.OwnerState;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final OwnerState owner;

    public ApiExceptionHandler(OwnerState owner) {
        this.owner = owner;
    }

    @ExceptionHandler(ApiFailure.class)
    public ResponseEntity<ApiError> handleFailure(ApiFailure failure) {
        return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore()).body(failure.error());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedBody(HttpServletRequest request) {
        var raw = request.getAttribute(CommandBodyAdvice.RAW_BODY);
        return handleFailure(owner.malformedResult(request.getHeader("Idempotency-Key"),
                raw == null ? "" : raw.toString()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader() {
        return handleFailure(new ApiFailure(400,
                new ApiError("MALFORMED_REQUEST", "Укажите непустой Idempotency-Key.")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleNonJsonBody(HttpServletRequest request) throws IOException {
        var body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return handleFailure(owner.malformedResult(request.getHeader("Idempotency-Key"), body));
    }
}
