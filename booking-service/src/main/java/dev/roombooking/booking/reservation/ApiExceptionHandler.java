package dev.roombooking.booking.reservation;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ProblemDetail handleRequestConstraintViolation(jakarta.validation.ConstraintViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "A request parameter failed validation");
        problem.setTitle("Invalid request parameter");
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    ProblemDetail handleIdempotencyKeyConflict(IdempotencyKeyConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Idempotency key conflict");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrityViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "bookings_active_slot_uq".equals(violation.getConstraintName())) {
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "The room is already booked for this slot");
                problem.setTitle("Slot unavailable");
                return problem;
            }
        }
        // A different integrity failure is a server defect, not a booking conflict.
        logger.error("Unexpected database integrity violation", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "The booking could not be saved");
    }
}
