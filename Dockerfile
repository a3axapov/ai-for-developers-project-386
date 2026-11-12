FROM node:26.13.0-bookworm-slim@sha256:add5c17c14752c54d2f3d519ec46a3815684d41c33f198bcb09fb998f7dcbf68 AS frontend
WORKDIR /workspace
RUN npm install --global npm@12.2.2
COPY package.json package-lock.json .npmrc ./
COPY frontend/package.json frontend/package.json
RUN npm ci
COPY frontend frontend
RUN npm run build --workspace frontend

FROM eclipse-temurin:26.0.2.1_1-jdk-noble@sha256:9c0e8eddecccc0d5679b317b0c90fa7103ad25cae33df1c00280675eb681c7f8 AS backend
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle gradle.lockfile ./
COPY gradle gradle
COPY src src
COPY api/server api/server
COPY --from=frontend /workspace/frontend/dist frontend/dist
RUN ./gradlew --no-daemon bootJar -PfrontendPrebuilt=true

FROM eclipse-temurin:26.0.2.1_1-jre-noble@sha256:adfd44c114916dc79fa9a24087fbac34252096edb9892d1c2d9d82db3f8947b3 AS runtime
WORKDIR /app
RUN groupadd --gid 10001 calendar \
    && useradd --uid 10001 --gid calendar --no-create-home --home-dir /app calendar
COPY --from=backend --chown=10001:10001 /workspace/build/libs/calendar.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
