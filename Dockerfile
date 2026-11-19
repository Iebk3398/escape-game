# 1) Interface Angular
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npx ng build

# 2) Backend Spring Boot (sert aussi l'interface)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist/frontend/browser ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# 3) Exécution (mémoire limitée pour tenir dans 512 Mo)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/escape.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx300m -Xss512k -XX:+UseSerialGC"
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
