
package dev.eventflow.orders.infrastructure.persistence.repository;

import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.domain.repository.OrderRepository;
import dev.eventflow.orders.domain.exception.OrderUpdateNotSupportedException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class JpaOrderRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17");

    @Autowired
    private OrderRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldPersistAndRetrieveOrder() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                productId,
                2,
                new BigDecimal("49.90")
        ));

        Order saved = repository.save(order);

        Order restored = repository.findById(saved.getId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        order.getId(), restored.getId()
                ),
                () -> assertEquals(
                        customerId, restored.getCustomerId()
                ),
                () -> assertEquals(
                        1, restored.getItems().size()
                ),
                () -> assertEquals(
                        productId,
                        restored.getItems().getFirst().productId()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("99.80")
                                .compareTo(restored.calculateTotal())
                )
        );
    }

    @Test
    void shouldReturnEmptyForUnknownOrder() {
        UUID unknownId = UUID.randomUUID();

        assertTrue(
                repository.findById(unknownId).isEmpty()
        );
    }

    @Test
    void shouldRetrieveOrderAfterTransactionCommit() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                productId,
                3,
                new BigDecimal("29.90")
        ));

        order.confirm();

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        UUID savedId = transaction.execute(status ->
                repository.save(order).getId()
        );

        assertNotNull(savedId);

        Order restored = transaction.execute(status -> {
            entityManager.clear();

            return repository.findById(savedId)
                    .orElseThrow();
        });

        assertNotNull(restored);

        assertAll(
                () -> assertEquals(savedId, restored.getId()),
                () -> assertEquals(
                        customerId,
                        restored.getCustomerId()
                ),
                () -> assertEquals(
                        OrderStatus.CONFIRMED,
                        restored.getStatus()
                ),
                () -> assertEquals(
                        1,
                        restored.getItems().size()
                ),
                () -> assertEquals(
                        productId,
                        restored.getItems().getFirst().productId()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("89.70")
                                .compareTo(restored.calculateTotal())
                )
        );
    }

    @Test
    void shouldRejectUpdatingExistingOrder() {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                1,
                new BigDecimal("100.00")
        ));

        repository.save(order);

        OrderUpdateNotSupportedException exception = assertThrows(
                OrderUpdateNotSupportedException.class,
                () -> repository.save(order)
        );

        assertEquals(
                "Updating existing orders is not supported yet",
                exception.getMessage()
        );
    }
}