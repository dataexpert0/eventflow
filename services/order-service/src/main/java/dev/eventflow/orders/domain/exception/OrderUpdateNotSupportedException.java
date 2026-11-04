
package dev.eventflow.orders.domain.exception;

public class OrderUpdateNotSupportedException
        extends RuntimeException {

    public OrderUpdateNotSupportedException() {
        super("Updating existing orders is not supported yet");
    }
}