FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
