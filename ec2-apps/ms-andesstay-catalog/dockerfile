# syntax=docker/dockerfile:1
# Build multi-etapa: compila con Maven (JDK) y corre con un JRE liviano.

# ---------- Etapa 1: build ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Primero solo lo necesario para resolver dependencias (aprovecha la cache de Docker:
# si no cambia el pom, no vuelve a bajar las dependencias).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Ahora el codigo y empaquetamos. -DskipTests porque el test por defecto levanta el
# contexto completo (necesitaria Postgres/Kafka/Rabbit arriba durante el build).
COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Usuario no-root por seguridad.
RUN useradd -r -u 1001 spring && chown spring:spring app.jar
USER spring

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]