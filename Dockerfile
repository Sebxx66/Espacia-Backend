# Imagen del backend de Espacia para desplegar en Render, Railway o cualquier servidor con Docker.
#   docker build -t espacia-backend .
#   docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... -e JWT_SECRET=... espacia-backend
# La configuración va en variables de entorno (ver "Configuración" en el README).

# 1. Compila con Maven
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/backend-*.jar app.jar

# 2. Imagen final: solo el entorno de ejecución de Java y la aplicación
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --no-create-home espacia
COPY --from=build /app/app.jar app.jar
USER espacia
# Memoria pensada para servidores pequeños (512 MB en el plan gratis de Render)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
# Render y Railway indican el puerto en la variable PORT; en otros servidores se usa 8080
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
