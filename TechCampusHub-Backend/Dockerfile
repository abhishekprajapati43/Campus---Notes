# ============================================================
# TechCampus Hub — backend image (Render / Railway / any Docker host)
#
# Stage 1: build the Spring Boot fat jar with the project's own
#          Maven wrapper (only-script mode — downloads Maven, no
#          wrapper jar needed, so nothing extra must be committed).
# Stage 2: slim JRE runtime.
#
# The image itself carries no environment-specific wiring: on Render
# the blueprint supplies PG_CONNECTION_URL (libpq form) and
# PgConnectionUrlEnvironmentPostProcessor converts it to JDBC settings
# at startup. Without it the app falls back to localhost dev defaults.
# ============================================================

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Keep the Maven JVM inside typical free-tier build limits.
ENV MAVEN_OPTS="-Xmx512m"

COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

ENV JAVA_OPTS=""

EXPOSE 8080

CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
