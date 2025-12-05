FROM eclipse-temurin:21-jdk

ARG JAR=target/*.jar

COPY ${JAR} cloud-gateway.jar

ENTRYPOINT ["java", "-jar", "cloud-gateway.jar"]

EXPOSE 9090
