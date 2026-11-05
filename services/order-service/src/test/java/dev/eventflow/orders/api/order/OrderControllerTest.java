
package dev.eventflow.orders.api.order;

import dev.eventflow.orders.application.usecase.create.CreateOrderCommand;
import dev.eventflow.orders.application.usecase.create.CreateOrderUseCase;
import dev.eventflow.orders.application.usecase.get.GetOrderUseCase;
import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private GetOrderUseCase getOrderUseCase;

    @Test
    void shouldCreateOrderAndReturn201() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = new Order(customerId);
        order.addItem(new OrderItem(
                productId,
                2,
                new BigDecimal("49.90")
        ));

        when(createOrderUseCase.execute(any(CreateOrderCommand.class)))
                .thenReturn(order);

        String request = """
                {
                  "customerId": "%s",
                  "items": [
                    {
                      "productId": "%s",
                      "quantity": 2,
                      "unitPrice": 49.90
                    }
                  ]
                }
                """.formatted(customerId, productId);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/v1/orders/" + order.getId()
                ))
                .andExpect(jsonPath("$.id").value(
                        order.getId().toString()
                ))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(99.80));
    }

    @Test
    void shouldReturnOrderWhenFound() throws Exception {
        Order order = new Order(UUID.randomUUID());

        when(getOrderUseCase.execute(order.getId()))
                .thenReturn(Optional.of(order));

        mockMvc.perform(get(
                        "/api/v1/orders/{id}",
                        order.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(
                        order.getId().toString()
                ))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturn404WhenOrderDoesNotExist() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(getOrderUseCase.execute(orderId))
                .thenReturn(Optional.empty());

        mockMvc.perform(get(
                        "/api/v1/orders/{id}",
                        orderId
                ))
                .andExpect(status().isNotFound());
    }


    @Test
    void shouldRejectMissingCustomerId() throws Exception {
        String request = """
            {
              "items": [
                {
                  "productId": "%s",
                  "quantity": 1,
                  "unitPrice": 10.00
                }
              ]
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void shouldRejectEmptyItems() throws Exception {
        String request = """
            {
              "customerId": "%s",
              "items": []
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void shouldRejectInvalidItemQuantity() throws Exception {
        String request = """
            {
              "customerId": "%s",
              "items": [
                {
                  "productId": "%s",
                  "quantity": -2,
                  "unitPrice": 49.90
                }
              ]
            }
            """.formatted(
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]")
                        .value(org.hamcrest.Matchers
                                .containsString("quantity")));
    }

    @Test
    void shouldRejectPriceWithExcessivePrecision()
            throws Exception {

        String request = """
            {
              "customerId": "%s",
              "items": [
                {
                  "productId": "%s",
                  "quantity": 1,
                  "unitPrice": 19.999
                }
              ]
            }
            """.formatted(
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]")
                        .value(org.hamcrest.Matchers
                                .containsString("unitPrice")));
    }
}