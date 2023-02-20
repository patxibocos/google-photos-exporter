FROM eclipse-temurin:19-jre-alpine
WORKDIR /app
COPY release/google-photos-exporter-exporter-cli.jar /app/google-photos-exporter-exporter-cli.jar
COPY entrypoint.sh /app

ENTRYPOINT ["/app/entrypoint.sh"]