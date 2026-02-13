# =========================
# ETAPA 1: BUILD (Compilación)
# =========================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests -B

# =========================
# ETAPA 2: RUNTIME (Ejecución)
# =========================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S luma && adduser -S luma -G luma
USER luma:luma

COPY --from=build /app/lumatrace-server/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV LUMATRACE_MASTER_KEY=""

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]