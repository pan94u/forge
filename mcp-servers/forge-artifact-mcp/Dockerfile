FROM eclipse-temurin:21-jre-alpine
COPY build/libs/forge-artifact-mcp-*-all.jar /app/app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
