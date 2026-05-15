package com.arquisoft.genai.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("ArquiSoft GenAI API")
                        .description("""
                                ## AI-Powered Software Architecture Generator
                                
                                This API generates complete software architecture recommendations using AI (GPT-4o-mini).
                                
                                ### Authentication
                                1. Register via `POST /api/auth/register`
                                2. Login via `POST /api/auth/login` to obtain a JWT token
                                3. Click **Authorize** and enter: `Bearer <your-token>`
                                4. Now you can call protected endpoints like `POST /api/generate`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ArquiSoft Team")
                                .email("contact@arquisoft.com"))
                        .license(new License().name("MIT")))
                .servers(java.util.List.of(new Server().url("/")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token (without 'Bearer ' prefix)")));
    }
}
