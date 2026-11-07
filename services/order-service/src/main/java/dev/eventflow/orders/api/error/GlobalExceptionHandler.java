
package dev.eventflow.orders.api.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;

import dev.eventflow.orders.domain.exception.OrderVersionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": "
                                + error.getDefaultMessage()
                )
                .sorted()
                .toList();

        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Validation failed");
        problem.setDetail("Request contains invalid fields");
        problem.setProperty("errors", errors);

        return handleExceptionInternal(
                ex,
                problem,
                headers,
                HttpStatus.BAD_REQUEST,
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Invalid request");
        problem.setDetail(ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(problem);
    }

    @ExceptionHandler(OrderVersionConflictException.class)
    public ProblemDetail handleVersionConflict(
            OrderVersionConflictException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.CONFLICT
        );

        problem.setTitle("Order version conflict");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleInvalidOrderTransition(
            IllegalStateException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.CONFLICT
        );

        problem.setTitle("Invalid order state transition");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ProblemDetail handlePersistenceVersionConflict(
            Exception exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.CONFLICT
        );

        problem.setTitle("Order version conflict");

        problem.setDetail(
                "The order was modified concurrently. " +
                        "Retrieve its latest state and try again."
        );

        return problem;
    }
}