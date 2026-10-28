package com.moapan;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

public class ApiError extends RuntimeException {
    final int status; final String code;
    public int statusCode(){return status;}
    public ApiError(int status,String code) { super(code); this.status=status; this.code=code; }
    @RestControllerAdvice
    static class Handler {
        @ExceptionHandler(ApiError.class) ResponseEntity<?> api(ApiError e) {
            var b=ResponseEntity.status(e.status).cacheControl(CacheControl.noStore());
            if(e.status==429) b.header("Retry-After","60");
            return b.body(java.util.Map.of("error",e.code));
        }
        @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class})
        ResponseEntity<?> conflict(Exception e) { return ResponseEntity.status(409).body(java.util.Map.of("error","conflict")); }
        @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,IllegalArgumentException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
        ResponseEntity<?> input(Exception e) { return ResponseEntity.badRequest().body(java.util.Map.of("error","invalid_input")); }
        @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
        ResponseEntity<?> denied(Exception e) { return ResponseEntity.status(403).body(java.util.Map.of("error","forbidden")); }
        @ExceptionHandler(Exception.class) ResponseEntity<?> failure(Exception e) { return ResponseEntity.status(503).body(java.util.Map.of("error","temporarily_unavailable")); }
    }
}
