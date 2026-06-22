# Production runtime image. The executable jar — with the React bundle baked
# into src/main/resources/static — is built beforehand by build-prod.sh, because
# this environment's Docker cannot resolve DNS during `docker build` RUN steps.
# This image therefore only COPYs the pre-built jar onto a cached JRE base.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/app.jar app.jar
EXPOSE 8080
ENV VAULT_ROOT=/vault \
    VAULT_WATCH_MODE=polling
ENTRYPOINT ["java", "-jar", "app.jar"]
