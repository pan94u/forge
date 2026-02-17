FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY . .
RUN ./gradlew :web-ide:backend:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/web-ide/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
