# --- Build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/target/*.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# MaxRAMPercentage : laisse la JVM utiliser 75 % de la mémoire du conteneur (décodage des images)
# headless : pas d'écran, requis pour manipuler des images dans un conteneur
# Démarrage plus rapide sur le CPU réduit de l'offre gratuite : compilateur léger + GC série
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Djava.awt.headless=true", "-jar", "/app/app.jar"]
