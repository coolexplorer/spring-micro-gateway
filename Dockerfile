FROM adoptopenjdk:11-jre-hotspot

ARG JAR_FILE=./target/*.jar
ARG PROFILE

ENV SPRING_PROFILE=$PROFILE
COPY ${JAR_FILE} webapp.jar

CMD ["java", "-Dspring.profiles.active=${SPRING_PROFILE}", "-jar", "webapp.jar"]