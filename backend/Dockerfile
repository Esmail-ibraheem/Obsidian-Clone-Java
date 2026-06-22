# Production image (placeholder for Phase 0; finalized in Phase 8 to also bundle
# the built React frontend into src/main/resources/static).
#
# NOTE: this environment's Docker cannot resolve DNS during `docker build` RUN
# steps, so the dependency-downloading build is NOT run here at image-build time.
# Phase 8 will assemble the prod image from a jar produced by a `docker run
# --dns` step (which has working DNS) rather than building deps inside the image.
FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app
COPY settings.gradle build.gradle ./
COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENV VAULT_ROOT=/vault
ENTRYPOINT ["java", "-jar", "app.jar"]
