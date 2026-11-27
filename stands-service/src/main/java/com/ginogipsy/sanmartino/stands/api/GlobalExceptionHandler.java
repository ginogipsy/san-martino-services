package com.ginogipsy.sanmartino.stands.api;

import com.ginogipsy.sanmartino.stands.service.*;
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

    @ExceptionHandler(ReviewNotFoundException.class)
    public ProblemDetail handleReviewNotFound(ReviewNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Review not found");
        return problem;
    }

    @ExceptionHandler(StandAccessDeniedException.class)
    public ProblemDetail handleStandAccessDenied(StandAccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Access denied for stand station");
        return problem;
    }

    @ExceptionHandler(StandKeypadValidationException.class)
    public ProblemDetail handleStandKeypadValidation(StandKeypadValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid keypad or order operation");
        return problem;
    }
}

