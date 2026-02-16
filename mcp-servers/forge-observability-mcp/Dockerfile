FROM eclipse-temurin:21-jre-alpine
COPY build/libs/forge-observability-mcp-*-all.jar /app/app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
