# Multi-stage build: compile with Maven+JDK, run on a slim JRE.
#
# NOTE on .m2-cache: this sandbox's Docker daemon has no outbound network
# (no proxy env vars reach the build container, so Maven Central is
# unreachable from inside `docker build` here). .m2-cache is this machine's
# already-populated ~/.m2, staged into the build context so the build runs
# fully offline (`mvn -o`). On a real CI runner with normal container
# network access, delete .m2-cache, drop the COPY below and the `-o` flag —
# Maven will simply resolve from Central as usual.
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /src
COPY .m2-cache /root/.m2
COPY pom.xml .
COPY src ./src
RUN mvn -o -q -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S pie && adduser -S pie -G pie
USER pie
WORKDIR /app
COPY --from=build /src/target/pie-risk-engine-*.jar app.jar
EXPOSE 8080
# No SPRING_PROFILES_ACTIVE default here on purpose — compose/K8s must set
# one explicitly (see application.yml's own comment on this).
ENTRYPOINT ["java", "-jar", "app.jar"]
