
package dev.eventflow.orders.api.order;

import dev.eventflow.orders.api.order.dto.CreateOrderRequest;
import dev.eventflow.orders.api.order.dto.OrderResponse;
import dev.eventflow.orders.application.usecase.create.CreateOrderCommand;
import dev.eventflow.orders.application.usecase.create.CreateOrderItemCommand;
import dev.eventflow.orders.application.usecase.create.CreateOrderUseCase;
import dev.eventflow.orders.application.usecase.get.GetOrderUseCase;
import dev.eventflow.orders.domain.model.Order;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            GetOrderUseCase getOrderUseCase
    ) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        var items = request.items()
                .stream()
                .map(item -> new CreateOrderItemCommand(
                        item.productId(),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();

        var command = new CreateOrderCommand(
                request.customerId(),
                items
        );

        Order order = createOrderUseCase.execute(command);

        URI location = URI.create(
                "/api/v1/orders/" + order.getId()
        );

        return ResponseEntity
                .created(location)
                .body(OrderResponse.fromDomain(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(
            @PathVariable UUID id
    ) {
        return getOrderUseCase.execute(id)
                .map(OrderResponse::fromDomain)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build()
                );
    }
}