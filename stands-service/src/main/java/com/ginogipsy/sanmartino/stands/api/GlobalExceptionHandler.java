package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.service.MenuItemNotFoundException;
import com.ginogipsy.sanmartino.stands.service.OwnerNotFoundException;
import com.ginogipsy.sanmartino.stands.service.StandNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StandNotFoundException.class)
    public ProblemDetail handleStandNotFound(StandNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Stand not found");
        return problem;
    }

    @ExceptionHandler(MenuItemNotFoundException.class)
    public ProblemDetail handleMenuItemNotFound(MenuItemNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Menu item not found");
        return problem;
    }

    @ExceptionHandler(OwnerNotFoundException.class)
    public ProblemDetail handleOwnerNotFound(OwnerNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Owner not found");
        return problem;
    }
}
