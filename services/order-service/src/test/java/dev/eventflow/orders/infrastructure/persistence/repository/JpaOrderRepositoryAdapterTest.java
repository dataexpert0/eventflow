
package dev.eventflow.orders.infrastructure.persistence.repository;

import dev.eventflow.orders.domain.exception.OrderVersionConflictException;
import dev.eventflow.orders.domain.model.Order;
import dev.eventflow.orders.domain.model.OrderItem;
import dev.eventflow.orders.domain.model.OrderStatus;
import dev.eventflow.orders.domain.repository.OrderRepository;
import dev.eventflow.orders.infrastructure.persistence.entity.OrderEntity;

import org.springframework.jdbc.core.JdbcTemplate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void shouldRejectConcurrentUpdatesUsingJpaVersion() {
        Order order = new Order(UUID.randomUUID());
        Order saved = repository.save(order);

        UUID orderId = saved.getId();

        EntityManager managerA =
                entityManagerFactory.createEntityManager();

        EntityManager managerB =
                entityManagerFactory.createEntityManager();

        try {
            // Both transactions begin independently.
            managerA.getTransaction().begin();
            managerB.getTransaction().begin();

            OrderEntity entityA =
                    managerA.find(OrderEntity.class, orderId);

            OrderEntity entityB =
                    managerB.find(OrderEntity.class, orderId);

            // Both transactions read the same version.
            assertEquals(0L, entityA.getVersion());
            assertEquals(0L, entityB.getVersion());

            // Transaction A confirms the order.
            entityA.updateStatus(OrderStatus.CONFIRMED);

            managerA.flush();
            managerA.getTransaction().commit();

            // Transaction B still holds version 0.
            entityB.updateStatus(OrderStatus.CANCELLED);

            // Hibernate must reject the stale update.
            assertThrows(
                    OptimisticLockException.class,
                    managerB::flush
            );

        } finally {
            if (managerA.getTransaction().isActive()) {
                managerA.getTransaction().rollback();
            }

            if (managerB.getTransaction().isActive()) {
                managerB.getTransaction().rollback();
            }

            managerA.close();
            managerB.close();
        }

        // Confirm the winning transaction was preserved.
        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                orderId
        );

        Long persistedVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM orders WHERE id = ?",
                Long.class,
                orderId
        );

        assertAll(
                () -> assertEquals(
                        "CONFIRMED",
                        persistedStatus
                ),
                () -> assertEquals(
                        1L,
                        persistedVersion
                )
        );
    }

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
    void shouldUpdateExistingPendingOrder() {
        UUID customerId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                firstProductId,
                1,
                new BigDecimal("50.00")
        ));

        Order saved = repository.save(order);

        saved.addItem(new OrderItem(
                secondProductId,
                2,
                new BigDecimal("25.00")
        ));

        repository.save(saved);

        Order restored = repository.findById(saved.getId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(saved.getId(), restored.getId()),
                () -> assertEquals(2, restored.getItems().size()),
                () -> assertEquals(
                        0,
                        new BigDecimal("100.00")
                                .compareTo(restored.calculateTotal())
                )
        );
    }

    @Test
    void shouldPreserveItemIdAndCreatedAtAfterUpdate() {
        UUID productId = UUID.randomUUID();

        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                productId,
                2,
                new BigDecimal("25.00")
        ));

        Order saved = repository.save(order);

        UUID originalItemId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM order_items
                WHERE order_id = ? AND product_id = ?
                """,
                UUID.class,
                order.getId(),
                productId
        );

        OffsetDateTime originalCreatedAt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT created_at FROM orders
                        WHERE id = ?
                        """,
                        (rs, rowNum) -> rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        ),
                        order.getId()
                );

        // Rebuild the aggregate with updated item quantity.
        Order updatedOrder = Order.restore(
                order.getId(),
                order.getCustomerId(),
                OrderStatus.PENDING,
                List.of(new OrderItem(
                        productId,
                        5,
                        new BigDecimal("25.00")
                )),
                saved.getVersion()
        );

        repository.save(updatedOrder);

        UUID updatedItemId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM order_items
                WHERE order_id = ? AND product_id = ?
                """,
                UUID.class,
                order.getId(),
                productId
        );

        OffsetDateTime updatedCreatedAt =
                jdbcTemplate.queryForObject(
                        """
                        SELECT created_at FROM orders
                        WHERE id = ?
                        """,
                        (rs, rowNum) -> rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        ),
                        order.getId()
                );

        Integer updatedQuantity = jdbcTemplate.queryForObject(
                """
                SELECT quantity FROM order_items
                WHERE order_id = ? AND product_id = ?
                """,
                Integer.class,
                order.getId(),
                productId
        );

        assertAll(
                () -> assertEquals(originalItemId, updatedItemId),
                () -> assertEquals(
                        originalCreatedAt.toInstant(),
                        updatedCreatedAt.toInstant()
                ),
                () -> assertEquals(5, updatedQuantity)
        );
    }

    @Test
    void shouldSynchronizeOrderItemsWithoutLosingIdentity() {
        UUID customerId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                firstProductId,
                2,
                new BigDecimal("30.00")
        ));

        Order saved = repository.save(order);

        UUID firstItemId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM order_items
                WHERE order_id = ? AND product_id = ?
                """,
                UUID.class,
                order.getId(),
                firstProductId
        );

        // Add a new product and update the existing one.
        Order expanded = Order.restore(
                order.getId(),
                customerId,
                OrderStatus.PENDING,
                List.of(
                        new OrderItem(
                                firstProductId,
                                5,
                                new BigDecimal("30.00")
                        ),
                        new OrderItem(
                                secondProductId,
                                1,
                                new BigDecimal("20.00")
                        )
                ),
                saved.getVersion()
        );

        Order savedExpanded = repository.save(expanded);

        UUID firstItemIdAfterUpdate =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id FROM order_items
                        WHERE order_id = ? AND product_id = ?
                        """,
                        UUID.class,
                        order.getId(),
                        firstProductId
                );

        UUID secondItemId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM order_items
                WHERE order_id = ? AND product_id = ?
                """,
                UUID.class,
                order.getId(),
                secondProductId
        );

        assertAll(
                () -> assertEquals(
                        firstItemId,
                        firstItemIdAfterUpdate
                ),
                () -> assertNotNull(secondItemId)
        );

        // Remove the first product from the aggregate.
        Order reduced = Order.restore(
                order.getId(),
                customerId,
                OrderStatus.PENDING,
                List.of(new OrderItem(
                        secondProductId,
                        1,
                        new BigDecimal("20.00")
                )),
                savedExpanded.getVersion()
        );

        repository.save(reduced);

        Order restored = repository.findById(order.getId())
                .orElseThrow();

        Integer removedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM order_items
                WHERE order_id = ? AND product_id = ?
                """,
                Integer.class,
                order.getId(),
                firstProductId
        );

        UUID remainingItemId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM order_items
                WHERE order_id = ? AND product_id = ?
                """,
                UUID.class,
                order.getId(),
                secondProductId
        );

        assertAll(
                () -> assertEquals(0, removedCount),
                () -> assertEquals(
                        secondItemId,
                        remainingItemId
                ),
                () -> assertEquals(
                        1,
                        restored.getItems().size()
                ),
                () -> assertEquals(
                        secondProductId,
                        restored.getItems().getFirst().productId()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("20.00")
                                .compareTo(restored.calculateTotal())
                )
        );
    }

    @Test
    void shouldUpdateTimestampWithoutChangingCreationDate() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                productId,
                1,
                new BigDecimal("25.00")
        ));

        Order saved = repository.save(order);

        OffsetDateTime originalCreatedAt =
                jdbcTemplate.queryForObject(
                        "SELECT created_at FROM orders WHERE id = ?",
                        (rs, rowNum) -> rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        ),
                        order.getId()
                );

        // Set an older timestamp to make the test deterministic.
        jdbcTemplate.update(
                """
                UPDATE orders
                SET updated_at = created_at - INTERVAL '1 day'
                WHERE id = ?
                """,
                order.getId()
        );

        OffsetDateTime previousUpdatedAt =
                jdbcTemplate.queryForObject(
                        "SELECT updated_at FROM orders WHERE id = ?",
                        (rs, rowNum) -> rs.getObject(
                                "updated_at",
                                OffsetDateTime.class
                        ),
                        order.getId()
                );

        Order updatedOrder = Order.restore(
                order.getId(),
                customerId,
                OrderStatus.PENDING,
                List.of(new OrderItem(
                        productId,
                        3,
                        new BigDecimal("25.00")
                )),
                saved.getVersion()
        );

        repository.save(updatedOrder);

        OffsetDateTime currentCreatedAt =
                jdbcTemplate.queryForObject(
                        "SELECT created_at FROM orders WHERE id = ?",
                        (rs, rowNum) -> rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        ),
                        order.getId()
                );

        OffsetDateTime currentUpdatedAt =
                jdbcTemplate.queryForObject(
                        "SELECT updated_at FROM orders WHERE id = ?",
                        (rs, rowNum) -> rs.getObject(
                                "updated_at",
                                OffsetDateTime.class
                        ),
                        order.getId()
                );

        assertAll(
                () -> assertEquals(
                        originalCreatedAt.toInstant(),
                        currentCreatedAt.toInstant()
                ),
                () -> assertTrue(
                        currentUpdatedAt.isAfter(previousUpdatedAt)
                ),
                () -> assertFalse(
                        currentUpdatedAt.isBefore(currentCreatedAt)
                )
        );
    }

    @Test
    void shouldReturnPersistenceVersionAfterSavingOrder() {
        Order order = new Order(UUID.randomUUID());

        order.addItem(new OrderItem(
                UUID.randomUUID(),
                1,
                new BigDecimal("50.00")
        ));

        Order saved = repository.save(order);

        assertNotNull(saved.getVersion());
        assertEquals(0L, saved.getVersion());

        Order restored = repository.findById(saved.getId())
                .orElseThrow();

        assertEquals(saved.getVersion(), restored.getVersion());
    }

    @Test
    void shouldRejectUpdatingOrderWithStaleVersion() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = new Order(customerId);

        order.addItem(new OrderItem(
                productId,
                1,
                new BigDecimal("25.00")
        ));

        Order saved = repository.save(order);

        // Two independent reads of the same version.
        Order firstRead = repository.findById(saved.getId())
                .orElseThrow();

        Order secondRead = repository.findById(saved.getId())
                .orElseThrow();

        assertEquals(firstRead.getVersion(), secondRead.getVersion());

        // First operation updates the order.
        firstRead.addItem(new OrderItem(
                productId,
                2,
                new BigDecimal("25.00")
        ));

        Order updated = repository.save(firstRead);

        assertEquals(
                firstRead.getVersion() + 1,
                updated.getVersion()
        );

        // Second operation uses the outdated version.
        secondRead.addItem(new OrderItem(
                productId,
                3,
                new BigDecimal("25.00")
        ));

        assertThrows(
                OrderVersionConflictException.class,
                () -> repository.save(secondRead)
        );

        // The first operation must remain intact.
        Order restored = repository.findById(saved.getId())
                .orElseThrow();

        assertAll(
                () -> assertEquals(
                        updated.getVersion(),
                        restored.getVersion()
                ),
                () -> assertEquals(
                        3,
                        restored.getItems().getFirst().quantity()
                ),
                () -> assertEquals(
                        0,
                        new BigDecimal("75.00")
                                .compareTo(restored.calculateTotal())
                )
        );
    }
}