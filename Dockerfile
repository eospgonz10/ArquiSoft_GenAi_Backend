# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (better layer caching)
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install Graphviz (required by PlantUML for some diagram types)
RUN apk add --no-cache graphviz

# Non-root user for security
# Create diagrams directory and ensure ownership so the non-root user can write
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
  && mkdir -p /app/diagrams \
  && chown -R appuser:appgroup /app/diagrams

# Switch to non-root user
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
