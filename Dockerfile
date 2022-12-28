FROM gradle:7.6.0-jdk19 AS builder
WORKDIR /app
COPY . /app
RUN gradle shadowJar

FROM amazoncorretto:19 as runner
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar /app/google-photos-github-exporter.jar
COPY --from=builder /app/entrypoint.sh /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
