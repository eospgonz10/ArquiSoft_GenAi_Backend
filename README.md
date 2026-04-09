# ArquiSoft GenAI Backend

> **AI-powered software architecture generator** — Spring Boot 3 REST API that uses GPT-4o-mini to generate software architecture recommendations (C4/UML diagrams, quality attributes, tech stack, architectural decisions) from business requirements.

---

## 📋 Table of Contents
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [API Endpoints](#api-endpoints)
- [Project Structure](#project-structure)
- [Running Tests](#running-tests)
- [AI Provider Configuration](#ai-provider-configuration)
- [Stage 6 – History (Planned)](#stage-6--history-planned)

---

## Requirements

| Tool | Version |
|------|---------|
| Docker | 24+ |
| Docker Compose | v2 |
| Java (local dev) | 17+ |
| Maven (local dev) | 3.9+ |

> **Note:** To use real AI generation, you need an [OpenAI API key](https://platform.openai.com/api-keys).
> New accounts receive $5 in free credits. The app defaults to `gpt-4o-mini` (~$0.00070 per call).

---

## Quick Start

### 1. Clone and configure

```bash
git clone <repo-url>
cd ArquiSoft_GenAi_Backend
```

Create a `.env` file in the project root (never commit this):

```env
OPENAI_API_KEY=sk-your-openai-key-here
JWT_SECRET=dGhpcyBpcyBhIHZlcnkgbG9uZyBzZWNyZXQga2V5IGZvciBqd3QgYXV0aGVudGljYXRpb24gMjAyNA==
DB_USER=arquisoft
DB_PASSWORD=arquisoft123
```

### 2. Start with Docker Compose

```bash
docker-compose up --build
```

This starts:
- **PostgreSQL 15** on port `5432`
- **ArquiSoft API** on port `8080`

### 3. Verify it's running

```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{"status": "UP", "service": "ArquiSoft GenAI Backend", "version": "1.0.0"}
```

### 4. Open Swagger UI

Navigate to: **http://localhost:8080/swagger-ui.html**

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | *(required)* | Your OpenAI API key |
| `OPENAI_MODEL` | `gpt-4o-mini` | OpenAI model to use |
| `OPENAI_MAX_RETRIES` | `2` | Retry attempts on API failure |
| `JWT_SECRET` | *(see below)* | Base64-encoded JWT signing secret |
| `JWT_EXPIRATION_MS` | `86400000` | JWT expiry in ms (24h) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/arquisoft_db` | PostgreSQL URL |
| `DB_USER` | `arquisoft` | DB username |
| `DB_PASSWORD` | `arquisoft123` | DB password |
| `AI_PROVIDER` | `openai` | `openai` or `mock` (no API key needed) |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev` or `prod` |

> **JWT Secret generation:**
> ```bash
> openssl rand -base64 64
> ```

---

## API Endpoints

### 🔓 Public Endpoints (no auth required)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health` | Health check |
| `POST` | `/api/auth/register` | Register new user → returns JWT |
| `POST` | `/api/auth/login` | Login → returns JWT |

### 🔒 Protected Endpoints (JWT required)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/generate` | Generate software architecture with AI |

### Example Flow

**1. Register:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "architect@example.com", "password": "MySecret123!"}'
```

**2. Login → get token:**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "architect@example.com", "password": "MySecret123!"}'
```

**3. Generate architecture:**
```bash
curl -X POST http://localhost:8080/api/generate \
  -H "Authorization: Bearer <your-jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "fintech",
    "qualityAttributes": ["security", "scalability", "performance"],
    "techStackConstraints": ["Java", "PostgreSQL", "Docker"],
    "naturalLanguageDescription": "A payment processing platform handling 10k TPS with PCI-DSS compliance"
  }'
```

**Response structure:**
```json
{
  "style": "Microservices",
  "qualityAttributes": ["security", "scalability"],
  "diagrams": {
    "C4-Context": "@startuml\n...\n@enduml",
    "C4-Container": "@startuml\n...\n@enduml"
  },
  "documentation": "# Fintech Architecture\n## Overview\n...",
  "techStack": ["Java 17", "Spring Boot 3", "PostgreSQL 15"],
  "decisions": ["Use JWT for stateless auth", "..."]
}
```

---

## Project Structure

```
src/main/java/com/arquisoft/genai/
├── domain/                  ← Entities + repository interfaces (no framework deps)
│   ├── model/User.java
│   └── repository/UserRepository.java
├── application/             ← Use cases, ports, exceptions, validation
│   ├── model/               ← ArchitectureInput, ArchitectureOutput
│   ├── port/                ← AiProvider, AuthService (interfaces)
│   ├── usecase/             ← AuthUseCase, GenerateArchitectureUseCase
│   ├── exception/           ← Custom exceptions
│   └── validation/          ← ArchitectureResponseValidator, DiagramSanitizer
├── infrastructure/          ← Spring/JPA/OpenAI implementations
│   ├── ai/                  ← OpenAiProvider, MockAiProvider
│   ├── persistence/         ← SpringUserRepository, UserRepositoryImpl
│   ├── security/            ← JwtTokenProvider, JwtAuthFilter, SecurityConfig
│   └── config/              ← OpenApiConfig, RestTemplateConfig
└── interfaces/              ← HTTP layer
    ├── controller/          ← AuthController, ArchitectureController, HealthController
    ├── dto/                 ← Request/Response DTOs
    ├── mapper/              ← MapStruct ArchitectureMapper
    └── exception/           ← GlobalExceptionHandler
```

---

## Running Tests

```bash
# Unit tests (no DB or API key needed)
mvn test

# With coverage report
mvn verify
```

Tests use **H2 in-memory database** and the **Mock AI provider** — no external services required.

---

## AI Provider Configuration

Switch between providers using the `AI_PROVIDER` environment variable:

| Value | Description |
|-------|-------------|
| `openai` (default) | Calls GPT-4o-mini via OpenAI API |
| `mock` | Returns realistic hardcoded responses (no API key needed) |

**For local development without spending API credits:**
```bash
AI_PROVIDER=mock docker-compose up
```

---

## Stage 6 – History (Planned)

The architecture is prepared for an optional **Generation History** feature:

- `GET /api/history` — list all generations for the authenticated user
- `GET /api/history/{id}` — retrieve a specific generation

This will require adding an `ArchitectureRequest` entity and its JPA repository.
Domain interfaces are already structured to support this extension with minimal changes.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.3 |
| Language | Java 17 |
| Security | Spring Security + JWT (jjwt 0.12) |
| Persistence | Spring Data JPA + PostgreSQL 15 |
| AI Integration | OpenAI GPT-4o-mini |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Mapping | MapStruct 1.5 |
| Lombok | 1.18 |
| Testing | JUnit 5 + Mockito + H2 |
| Containers | Docker + Docker Compose |
