# Build and run on Render when native "Java" runtime is unavailable (use Docker).
FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -B package -DskipTests \
    && cp target/$(ls target | grep '\.jar$' | grep -v plain) application.jar

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/application.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
