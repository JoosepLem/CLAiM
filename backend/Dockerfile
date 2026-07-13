FROM eclipse-temurin:25-jdk-alpine AS builder
RUN apk add --no-cache binutils
WORKDIR /app
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q || true
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jdk-alpine AS jlink
RUN jlink \
    --no-header-files \
    --no-man-pages \
    --compress=zip-6 \
    --strip-debug \
    --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.transaction.xa,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.management \
    --output /custom-jre

FROM alpine:3.21
RUN apk add --no-cache tzdata
COPY --from=jlink /custom-jre /opt/jre
COPY --from=builder /app/build/libs/*.jar /opt/app/app.jar
EXPOSE 8080
ENV JAVA_HOME=/opt/jre
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/opt/app/app.jar"]
