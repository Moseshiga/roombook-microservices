FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine

ARG MODULE
WORKDIR /app
RUN test -n "$MODULE" && addgroup -S app && adduser -S -G app app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar /app/app.jar

USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
