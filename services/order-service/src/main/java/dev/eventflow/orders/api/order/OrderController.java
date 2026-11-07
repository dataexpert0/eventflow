
package dev.eventflow.orders.api.order;

import dev.eventflow.orders.api.order.dto.CreateOrderRequest;
import dev.eventflow.orders.api.order.dto.OrderResponse;
import dev.eventflow.orders.application.usecase.create.CreateOrderCommand;
import dev.eventflow.orders.application.usecase.create.CreateOrderItemCommand;
import dev.eventflow.orders.application.usecase.create.CreateOrderUseCase;
import dev.eventflow.orders.application.usecase.get.GetOrderUseCase;
import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.application.usecase.confirm.ConfirmOrderUseCase;
import dev.eventflow.orders.application.usecase.cancel.CancelOrderUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Tag(
        name = "Orders",
        description = "Order creation and retrieval operations"
)

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderController(
            CreateOrderUseCase createOrderUseCase,
            GetOrderUseCase getOrderUseCase,
            ConfirmOrderUseCase confirmOrderUseCase,
            CancelOrderUseCase cancelOrderUseCase
    ) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @Operation(
            summary = "Create a new order",
            description = "Creates an order with PENDING status."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Order created successfully"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid order request"
    )

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

    @Operation(
            summary = "Retrieve an order by ID",
            description = "Returns an existing order using its UUID."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Order found"
    )
    @ApiResponse(
            responseCode = "400",
            description = "Invalid order identifier"
    )
    @ApiResponse(
            responseCode = "404",
            description = "Order not found"
    )

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

    @Operation(
            summary = "Confirm an order",
            description = "Confirms a pending order containing at least one item."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order confirmed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid order identifier"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Invalid order state or version conflict"
            )
    })
    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(
            @PathVariable UUID id
    ) {
        return confirmOrderUseCase.execute(id)
                .map(order -> ResponseEntity.ok(
                        OrderResponse.fromDomain(order)
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(
            summary = "Cancel an order",
            description = "Cancels an order that is currently pending."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Order cancelled successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid order identifier"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Order not found"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Invalid order state or version conflict"
            )
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable UUID id
    ) {
        return cancelOrderUseCase.execute(id)
                .map(order -> ResponseEntity.ok(
                        OrderResponse.fromDomain(order)
                ))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}