FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/target/compliance-checker.jar app.jar
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
