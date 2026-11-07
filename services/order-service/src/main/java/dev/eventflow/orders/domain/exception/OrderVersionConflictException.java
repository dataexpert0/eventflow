
package dev.eventflow.orders.domain.exception;

public class OrderVersionConflictException
        extends RuntimeException {

    public OrderVersionConflictException() {
        super("Order has been modified by another operation");
    }
}