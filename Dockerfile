# ---- build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Cache the dependency tree separately from the source.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q -DskipTests package

# ---- runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 sithiyama
COPY --from=build /build/target/sithiyama-backend-*.jar app.jar
USER sithiyama

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
