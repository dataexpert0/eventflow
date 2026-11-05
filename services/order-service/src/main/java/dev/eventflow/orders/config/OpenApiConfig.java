
package dev.eventflow.orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EventFlow - Order Service API")
                        .version("0.2.0")
                        .description(
                                "REST API for creating and retrieving "
                                        + "orders in the EventFlow platform."
                        ));
    }
}