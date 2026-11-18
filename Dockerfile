FROM node:26.13.1-bookworm-slim@sha256:5df79989ed685e09f0a98019db4cebfb22fb9df1a53c0161664a011f7ff6b0f1 AS frontend
WORKDIR /workspace
RUN npm install --global npm@12.2.2
COPY package.json package-lock.json .npmrc ./
COPY frontend/package.json frontend/package.json
RUN npm ci
COPY frontend frontend
RUN npm run build --workspace frontend

FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS backend
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle gradle.lockfile ./
COPY gradle gradle
COPY src src
COPY api/server api/server
COPY --from=frontend /workspace/frontend/dist frontend/dist
RUN ./gradlew --no-daemon bootJar -PfrontendPrebuilt=true

FROM eclipse-temurin:25.0.5_8-jre-noble@sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce AS runtime
WORKDIR /app
RUN groupadd --gid 10001 calendar \
    && useradd --uid 10001 --gid calendar --no-create-home --home-dir /app calendar
COPY --from=backend --chown=10001:10001 /workspace/build/libs/calendar.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
