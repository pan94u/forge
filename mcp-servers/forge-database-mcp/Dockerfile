FROM eclipse-temurin:21-jre-alpine
COPY build/libs/forge-database-mcp-*-all.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
