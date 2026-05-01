FROM eclipse-temurin:25.0.3_9-jre-alpine
WORKDIR /app
COPY release/exporter-cli.jar /app/exporter-cli.jar
COPY entrypoint.sh /app

ENTRYPOINT ["/app/entrypoint.sh"]