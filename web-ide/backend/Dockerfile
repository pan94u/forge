FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache bash git grep findutils python3
RUN mkdir -p /workspace /app/data
WORKDIR /app
COPY build/libs/backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
