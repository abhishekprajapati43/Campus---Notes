package com.example.techcampushubbackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI metadata. Interactive docs: {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "bearerAuth";

    @Bean
    public OpenAPI techCampusHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TechCampus Hub API")
                        .description("""
                                REST API for the TechCampus Hub learning portal.

                                Authentication is stateless JWT: obtain a token from
                                `POST /api/auth/login` and send it as
                                `Authorization: Bearer <token>`.

                                Role rules:
                                - STUDENT: courses, purchases, notes, favorites, feedback, notifications
                                - ADMIN: everything under `/api/admin/**` (content CRUD, purchase approval,
                                  validity extension, notifications)
                                """)
                        .version("v1")
                        .contact(new Contact().name("TechCampus Hub").email("support@techcampushub.com")))
                .components(new Components()
                        .addSecuritySchemes(SCHEME, new SecurityScheme()
                                .name(SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
