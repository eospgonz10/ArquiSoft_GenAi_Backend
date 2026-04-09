package com.arquisoft.genai.infrastructure.ai;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock AI provider for local development and testing.
 * Activated when ai.provider=mock in application config.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock")
@Slf4j
public class MockAiProvider implements AiProvider {

    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        log.info("[MOCK] Generating architecture for domain: '{}'", input.getDomain());

        Map<String, String> diagrams = new LinkedHashMap<>();
        diagrams.put("C4-Context", buildC4Context(input.getDomain()));
        diagrams.put("C4-Container", buildC4Container(input.getDomain()));
        diagrams.put("Sequence-Auth", buildSequenceDiagram());

        List<String> qaList = (input.getQualityAttributes() != null && !input.getQualityAttributes().isEmpty())
                ? input.getQualityAttributes()
                : List.of("security", "scalability", "maintainability");

        return ArchitectureOutput.builder()
                .style("Microservices")
                .qualityAttributes(qaList)
                .diagrams(diagrams)
                .documentation(buildDocumentation(input.getDomain()))
                .techStack(List.of("Java 17", "Spring Boot 3", "PostgreSQL 15", "Docker", "JWT", "Redis"))
                .decisions(List.of(
                        "Use Microservices to allow independent scaling and deployment of services",
                        "JWT for stateless, distributed authentication across services",
                        "PostgreSQL for ACID compliance and relational data integrity",
                        "RESTful API with OpenAPI/Swagger for broad client compatibility",
                        "Docker + Docker Compose for consistent environment across dev/prod"
                ))
                .build();
    }

    private String buildC4Context(String domain) {
        return """
                @startuml
                !include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml
                title System Context Diagram - %s System
                Person(user, "End User", "Uses the %s platform")
                System(system, "%s System", "Core business application")
                System_Ext(ai, "OpenAI API", "AI architecture generation")
                System_Ext(email, "Email Service", "Notifications")
                Rel(user, system, "Uses", "HTTPS")
                Rel(system, ai, "Generates architecture", "REST/HTTPS")
                Rel(system, email, "Sends notifications", "SMTP")
                @enduml
                """.formatted(domain, domain, domain);
    }

    private String buildC4Container(String domain) {
        return """
                @startuml
                !include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml
                title Container Diagram - %s System
                Person(user, "End User")
                System_Boundary(c1, "%s System") {
                    Container(api, "API Gateway", "Spring Boot 3", "REST API, JWT Auth")
                    Container(db, "Database", "PostgreSQL 15", "User data, persistence")
                    Container(cache, "Cache", "Redis", "Session & rate limiting")
                }
                System_Ext(openai, "OpenAI API", "GPT-4o-mini")
                Rel(user, api, "API calls", "HTTPS/JSON")
                Rel(api, db, "Read/Write", "JPA")
                Rel(api, cache, "Cache", "Redis Protocol")
                Rel(api, openai, "Generate", "REST/HTTPS")
                @enduml
                """.formatted(domain, domain);
    }

    private String buildSequenceDiagram() {
        return """
                @startuml
                title Authentication Flow
                actor User
                participant "API Gateway" as API
                participant "Auth Service" as Auth
                database "PostgreSQL" as DB
                User -> API : POST /api/auth/login {email, password}
                API -> Auth : validate credentials
                Auth -> DB : findByEmail(email)
                DB --> Auth : User entity
                Auth -> Auth : BCrypt.verify(password, hash)
                Auth --> API : JWT token (24h)
                API --> User : 200 OK {token, expiresIn}
                @enduml
                """;
    }

    private String buildDocumentation(String domain) {
        return """
                # %s System Architecture

                ## Overview
                This architecture follows a **Microservices** style, optimized for the **%s** domain.
                It prioritizes scalability, security, and maintainability.

                ## Architectural Style
                **Microservices** — each business capability is deployed as an independent service,
                communicating via REST APIs with JWT authentication.

                ## Key Components
                | Component | Technology | Responsibility |
                |-----------|-----------|----------------|
                | API Gateway | Spring Boot 3 | Request routing, JWT validation |
                | Database | PostgreSQL 15 | Persistent storage |
                | Cache | Redis | Performance, rate limiting |
                | AI Provider | OpenAI GPT-4o-mini | Architecture generation |

                ## Quality Attributes
                - **Security**: All endpoints protected by JWT; BCrypt password hashing
                - **Scalability**: Stateless services enable horizontal scaling
                - **Maintainability**: Clean Architecture, SOLID, comprehensive tests

                ## Deployment
                All services containerized via Docker. Use `docker-compose up` for local development.
                """.formatted(domain, domain);
    }
}
