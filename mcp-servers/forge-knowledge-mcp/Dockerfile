FROM eclipse-temurin:21-jre-alpine
COPY build/libs/forge-knowledge-mcp-*-all.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
