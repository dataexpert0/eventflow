
package dev.eventflow.notifications.api;

import dev.eventflow.notifications.application.model.PageResult;
import dev.eventflow.notifications.application.usecase.GetNotificationsByOrderIdUseCase;
import dev.eventflow.notifications.domain.model.NotificationSummary;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetNotificationsByOrderIdUseCase useCase;

    @Test
    void shouldReturnNotificationsForOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        NotificationSummary notification =
                new NotificationSummary(
                        notificationId,
                        eventId,
                        orderId,
                        "Order created successfully",
                        OffsetDateTime.parse(
                                "2026-11-07T22:00:00Z"
                        )
                );

        PageResult<NotificationSummary> result =
                new PageResult<>(
                        List.of(notification),
                        0,
                        20,
                        1,
                        1
                );

        when(useCase.execute(orderId, 0, 20))
                .thenReturn(result);

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("orderId", orderId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id")
                        .value(notificationId.toString()))
                .andExpect(jsonPath("$.content[0].eventId")
                        .value(eventId.toString()))
                .andExpect(jsonPath("$.content[0].orderId")
                        .value(orderId.toString()))
                .andExpect(jsonPath("$.content[0].message")
                        .value("Order created successfully"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(useCase).execute(orderId, 0, 20);
    }

    @Test
    void shouldReturnEmptyPageWhenNoNotificationsExist()
            throws Exception {
        UUID orderId = UUID.randomUUID();

        when(useCase.execute(orderId, 0, 20))
                .thenReturn(new PageResult<>(
                        List.of(),
                        0,
                        20,
                        0,
                        0
                ));

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("orderId", orderId.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(useCase).execute(orderId, 0, 20);
    }

    @Test
    void shouldAcceptCustomPagination() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(useCase.execute(orderId, 1, 5))
                .thenReturn(new PageResult<>(
                        List.of(),
                        1,
                        5,
                        8,
                        2
                ));

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("orderId", orderId.toString())
                                .param("page", "1")
                                .param("size", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(useCase).execute(orderId, 1, 5);
    }

    @Test
    void shouldRejectInvalidOrderId() throws Exception {
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("orderId", "invalid-uuid")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void shouldRejectNegativePage() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("orderId", orderId.toString())
                                .param("page", "-1")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void shouldRejectZeroPageSize() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("orderId", orderId.toString())
                                .param("size", "0")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }

    @Test
    void shouldRejectPageSizeAboveMaximum() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("orderId", orderId.toString())
                                .param("size", "101")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(useCase);
    }
}