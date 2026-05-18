package com.ginogipsy.sanmartino.saga.api;

import com.ginogipsy.sanmartino.saga.service.SagaNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SagaNotFoundException.class)
    public ProblemDetail handleSagaNotFound(SagaNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Saga not found");
        return problem;
    }
}
