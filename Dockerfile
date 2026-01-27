# =========================
# ETAPA 1: BUILD
# =========================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY . .
RUN mvn clean package -DskipTests -B

# =========================
# ETAPA 2: RUNTIME
# =========================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S luma && adduser -S luma -G luma
USER luma:luma

# Copia el JAR del módulo Cloud (ajusta el nombre si no es *-SNAPSHOT.jar)
COPY --from=build /app/lumatrace-cloud/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
