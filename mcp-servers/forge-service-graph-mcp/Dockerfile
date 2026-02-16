FROM eclipse-temurin:21-jre-alpine
COPY build/libs/forge-service-graph-mcp-*-all.jar /app/app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
