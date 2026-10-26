# =============================================================================
# Stage 1: Build JAR using Maven & JDK 21
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# =============================================================================
# Stage 2: Minimal, secure JRE 21 Runtime Image
# =============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="HireHub Team"
LABEL description="HireHub Recruitment Management API"

WORKDIR /app

# Create unprivileged user for security hardening
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Directory for uploaded files (resumes)
RUN mkdir -p /app/uploads/resumes && chown -R appuser:appgroup /app

# Install curl for container healthcheck
RUN apk add --no-cache curl

# Copy fat JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar /app/app.jar

USER appuser

EXPOSE 8080

# Production-tuned container JVM flags
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/api/health || exit 1
