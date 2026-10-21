# ----------------------------------------------------------------------------------------
# org.rd.fullstack.springboot-eda
# ----------------------------------------------------------------------------------------
# Dockerfile for containerizing a Spring Boot application using layer-based composition.
# The application also contains a statically generated Nuxt web application (Jamstack).
# The Spring Boot application can therefore provide both:
# - the web application
# - BFF-only SOA services
#
# IMPORTANT:
# Including a web application with SOA services (BFF only) is neither inherently wrong
# nor generally recommended. The solution architect's judgment is required.
# If this approach is used, the SOA services must be limited exclusively to BFF
# requirements.
#
# R. Demers, 2026.
# ----------------------------------------------------------------------------------------
# Specifications and constraints:
#
# 1. Multi-architecture
# The Dockerfile intentionally uses multi-architecture base-image names:
# eclipse-temurin:25-jdk
# amazoncorretto:25
#
# Docker selects the appropriate architecture for the build platform.
# Examples:
# x86_64/amd64
# ARM64/Apple Silicon
#
# No arm64v8/ prefix is required when the selected Docker image provides
# the required architecture.
#
# 2. Maven proxy
# If a proxy is required, copy the Maven configuration and use:
# RUN ./mvnw --settings ./maven-with-proxy.xml -U -B -e -f pom.xml clean prepare-package package
#
# Without a proxy, use:
# RUN ./mvnw -U -B -e -f pom.xml clean prepare-package package
#
# 3. Skip tests
# Tests can be skipped with:
# -Dmaven.test.skip=true
#
# 4. Build the image
# docker build --no-cache -t org-rd-fullstack/springboot-eda:unspecified .
#
# 5. Run the image
# docker run -it -p 8080:8080 -p 8081:8081 org-rd-fullstack/springboot-eda:unspecified
# OR ... 
# On a development machine, use host networking to make TCP/UDP services
# running in the container directly accessible from the host without explicitly publishing 
# individual ports.
# docker run --network host -it org-rd-fullstack/springboot-eda:unspecified
# Important:
#    Host networking is supported on Docker Desktop version 4.34 and later. 
#    To enable this feature:
#    - Sign in to your Docker account in Docker Desktop.
#    - Navigate to Settings.
#    - Under the Resources tab, select Network.
#    - Check the Enable host networking option.
#    - Select Apply and restart.
# This feature works in both directions. 
# This means you can access a server that is running in a container from your host and 
# you can access servers running on your host from any container that is started with 
# host networking enabled. TCP as well as UDP are supported as communication protocols.
#
# This feature applies to Linux containers only: 
# - Docker states that Docker Desktop host networking does not work with Windows containers.
#
# 6. Build for a specific architecture
# docker build --platform linux/amd64 -t org-rd-fullstack/springboot-eda:amd64 .
# docker build --platform linux/arm64 -t org-rd-fullstack/springboot-eda:arm64 .
#
# 7. Build a multi-architecture image
# docker buildx build --platform linux/amd64,linux/arm64 -t org-rd-fullstack/springboot-eda:unspecified .
#
# 8. Clear Docker cache and unused local images before a clean build:
# docker system prune -a
# ----------------------------------------------------------------------------------------

# ========================================================================================
# Builder stage
# ========================================================================================

# Java 25 + Debian-based image.
# This gives us apt-get for installing Node.js.
FROM maven:3.9.11-eclipse-temurin-25 AS builder
WORKDIR /application

# ----------------------------------------------------------------------------------------
# Copy application sources and build configuration.
# ----------------------------------------------------------------------------------------

COPY src ./src
COPY pom.xml .
COPY layers.xml .
COPY mvnw .
COPY .mvn ./.mvn
COPY build-web-app.xml .

# If a Maven proxy is required:
# COPY maven-with-proxy.xml .

# ----------------------------------------------------------------------------------------
# Prepare Maven Wrapper.
# ----------------------------------------------------------------------------------------

RUN chmod +x ./mvnw

# ----------------------------------------------------------------------------------------
# Remove locally installed Node modules.
# ----------------------------------------------------------------------------------------

RUN rm -rf src/frontend/node_modules

# ----------------------------------------------------------------------------------------
# Install Node.js 24.
# ----------------------------------------------------------------------------------------

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
        | gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_24.x nodistro main" \
        > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# ----------------------------------------------------------------------------------------
# Display versions.
# Useful for students when diagnosing build problems.
# ----------------------------------------------------------------------------------------

RUN java -version \
    && node --version \
    && npm --version

# ----------------------------------------------------------------------------------------
# NPM configuration.
# ----------------------------------------------------------------------------------------

RUN npm config set registry=https://registry.npmjs.org/

# ----------------------------------------------------------------------------------------
# Build the application.
# Without a proxy:
# ----------------------------------------------------------------------------------------

RUN mvn -U -B -e clean prepare-package package

# ----------------------------------------------------------------------------------------
# With a proxy:
# RUN mvn --settings ./maven-with-proxy.xml -U -B -e clean prepare-package package
# ----------------------------------------------------------------------------------------

# ----------------------------------------------------------------------------------------
# Prepare Spring Boot layers.
# ----------------------------------------------------------------------------------------

ARG JAR_FILE=target/*.jar
RUN cp ${JAR_FILE} application.jar
RUN java -Djarmode=tools \
    -jar application.jar \
    extract \
    --layers \
    --destination extracted

# ========================================================================================
# Runtime stage
# ========================================================================================

FROM amazoncorretto:25

# ----------------------------------------------------------------------------------------
# Image metadata.
# ----------------------------------------------------------------------------------------

ARG LABEL_TITLE="Please, provide a title."
ARG LABEL_DESCRIPTION="Please, provide a description."
ARG LABEL_CREATED="9999-99-99"

# Version: MAJOR.MINOR.REVISION-BUILD.
ARG LABEL_VERSION_MAJOR="1"
ARG LABEL_VERSION_MINOR="0"
ARG LABEL_VERSION_REVISION="0"
ARG LABEL_VERSION_BUILD="#1"

LABEL org.opencontainers.image.title="${LABEL_TITLE}" \
      org.opencontainers.image.description="${LABEL_DESCRIPTION}" \
      org.opencontainers.image.created="${LABEL_CREATED}" \
      org.opencontainers.image.version.major="${LABEL_VERSION_MAJOR}" \
      org.opencontainers.image.version.minor="${LABEL_VERSION_MINOR}" \
      org.opencontainers.image.version.revision="${LABEL_VERSION_REVISION}" \
      org.opencontainers.image.version.build="${LABEL_VERSION_BUILD}"

WORKDIR /application

# ----------------------------------------------------------------------------------------
# Copy Spring Boot layers.
# Order:
#   1. framework-dependencies
#   2. spring-boot-loader
#   3. corpo-dependencies
#   4. snapshot-dependencies
#   5. application
#
# The order goes from the least frequently modified content to the most frequently
# modified content.
# ----------------------------------------------------------------------------------------

COPY --from=builder /application/extracted/framework-dependencies/ ./
COPY --from=builder /application/extracted/spring-boot-loader/ ./
COPY --from=builder /application/extracted/corpo-dependencies/ ./
COPY --from=builder /application/extracted/snapshot-dependencies/ ./
COPY --from=builder /application/extracted/application/ ./

# ----------------------------------------------------------------------------------------
# Start the Spring Boot application.
# ----------------------------------------------------------------------------------------

ENTRYPOINT ["java", "-jar", "application.jar"]

# ----------------------------------------------------------------------------------------
# End of Dockerfile
# ----------------------------------------------------------------------------------------