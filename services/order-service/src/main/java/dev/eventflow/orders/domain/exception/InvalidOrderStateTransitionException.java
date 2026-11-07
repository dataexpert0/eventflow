
package dev.eventflow.orders.domain.exception;

public class InvalidOrderStateTransitionException
        extends IllegalStateException {

    public InvalidOrderStateTransitionException(
            String message
    ) {
        super(message);
    }
}
