FROM amazoncorretto:19
WORKDIR /app
COPY release/google-photos-github-exporter.jar /app/google-photos-github-exporter.jar
COPY entrypoint.sh /app

ENTRYPOINT ["/app/entrypoint.sh"]