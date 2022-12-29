FROM amazoncorretto:19
WORKDIR /app
COPY release/google-photos-exporter.jar /app/google-photos-exporter.jar
COPY entrypoint.sh /app

ENTRYPOINT ["/app/entrypoint.sh"]