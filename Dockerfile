FROM gradle:7.6.0-jdk8 AS builder
WORKDIR /app
COPY . /app
RUN gradle shadowJar

FROM openjdk:8-jre-slim as runner
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar /app/google-photos-github-exporter.jar

ENTRYPOINT ["java","-jar","/app/google-photos-github-exporter.jar"]