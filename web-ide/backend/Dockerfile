FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache bash git grep findutils python3 nodejs npm
RUN mkdir -p /workspace /app/data
WORKDIR /app
COPY build/libs/backend-0.1.0-SNAPSHOT.jar app.jar
ARG GIT_COMMIT=unknown
ARG GIT_BRANCH=unknown
ARG BUILD_TIME=unknown
ENV GIT_COMMIT=${GIT_COMMIT}
ENV GIT_BRANCH=${GIT_BRANCH}
ENV BUILD_TIME=${BUILD_TIME}

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
