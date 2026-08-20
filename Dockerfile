FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m -XX:MaxMetaspaceSize=192m"
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
