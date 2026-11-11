FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68 AS frontend
WORKDIR /workspace
RUN npm install --global npm@12.2.2
COPY package.json package-lock.json .npmrc ./
COPY frontend/package.json frontend/package.json
RUN npm ci
COPY api api
COPY scripts/generate-api.mjs scripts/generate-api.mjs
COPY frontend frontend
RUN npm run build

FROM eclipse-temurin:25.0.5_8-jdk-noble@sha256:95e350ad1eb738f943e141640f7e3d797e483359a3b33c3864255bfd1ad71f8a AS backend
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle gradle.lockfile ./
COPY gradle gradle
COPY src src
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
