package org.pyt.traveladvisor.exception;

import lombok.extern.slf4j.Slf4j;
import org.pyt.traveladvisor.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handle(RuntimeException e) {

        return ResponseEntity.status(404)
                .body(ApiResponse.failure(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {

        log.error("Unhandled error", e);

        return ResponseEntity.status(500)
                .body(ApiResponse.failure("Something went wrong"));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ValidationException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(e.getMessage()));
    }
}
