# ----------------------------------------------------------------------------------------
# org.rd.fullstack.springboot-kafkaflink
# ----------------------------------------------------------------------------------------
# 
# Dockerfile for containerizing a Springboot application that supports the composition per
# layer (layer). It also includes a WEB application in static mode (JamStack). This app is
# made with Nuxt.
#
# This approach promotes the reuse of different layers and boundaries disk space usage of 
# images. Moreover, it allows to have in a single deliverable SOA services (BFF only) of 
# the WEB application.
#
# IMPORTANT:
# Including a WEB application with SOA services (BBF only) is not a crime (nor a recommen-
# dation). The solution architect's judgment is required. If the approach is used, then 
# limit the SOA services to the needs of type BFF only.
#
# R. Demers; 2025.
# ----------------------------------------------------------------------------------------
# Specifications and constraints:
#
# 1. You must provide a valid base image for your operating system.
#    We suggest  :
#    - For x86 processor (Windows X86 processor)
#      debian:bullseye-slim as builder
#      amazoncorretto:17
#
#    - For arm64 processor (Macbook ARM processor)
#      arm64v8/debian:bullseye-slim as builder
#      arm64v8/amazoncorretto:17
#
# 2. You must provide a valid Maven configuration to build your image if you have a proxy.
#    - See line: RUN ./mvnw --settings maven-with-proxy.xml ...
#
# 3. You need to remove "--settings ./maven-with-proxy.xml" for building without proxy.
#    - RUN ./mvnw -U -B -e -f pom.xml clean prepare-package package
#
# 4. You can also suppress test execution using the next parameter: -Dmaven.test.skip=true.
#
# 5. The command to use to build the image:
#    - docker build --no-cache -t org-rd-fullstack/springboot-nuxt:unspecified .
#
# 6. The command to run the image:
#    - docker run -it -p8080:8080 -p8081:8081 org-rd-fullstack/springboot-nuxt:unspecified
#
# 7. To clear cache and local registry to do a full test
#    of the construction of your image:
#    - docker system prune -a
# ----------------------------------------------------------------------------------------
# Build the builder.
# 
#  FROM debian:bullseye-slim as builder as builder
   FROM arm64v8/debian:bullseye-slim as builder
WORKDIR /application
#
# We copy the sources, files and tools for the build process.
   COPY src ./src
   COPY pom.xml .
   COPY layers.xml .
   COPY mvnw .
   COPY .mvn ./.mvn
   COPY maven-with-proxy.xml .
   COPY web-app-jamstack.xml .
#
# We need to remove Node Modules from Web Application/App.
# We need to execute Apache/Maven.
    RUN rm -rf src/frontend/node_modules
    RUN chmod 777 ./mvnw
#
# Build requires Node.js and Java/JDK.
    RUN apt update -y
    RUN apt install ca-certificates-java ca-certificates curl gnupg -y
    RUN curl -fsSL https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key | gpg --dearmor -o /usr/share/keyrings/nodesource.gpg
    RUN echo "deb [signed-by=/usr/share/keyrings/nodesource.gpg] https://deb.nodesource.com/node_20.x nodistro main" | tee /etc/apt/sources.list.d/nodesource.list
    RUN apt update -y
    RUN apt install nodejs -y
    RUN apt install openjdk-17-jdk -y
#
# Setup NPM.
    RUN npm set registry=https://registry.npmjs.org/
    RUN npm config set strict-ssl false --global
#
# Build ... No proxy configuration required.
    RUN ./mvnw -U -B -e -f pom.xml clean prepare-package package
# Build ... With a proxy setup. See maven-with-proxy.xml.
#   RUN ./mvnw --settings maven-with-proxy.xml -U -B -e -f pom.xml clean prepare-package package
#
# We prepare the SpringBoot application layers.
    ARG JAR_FILE=target/*.jar
    RUN cp ${JAR_FILE} application.jar
    RUN java -Djarmode=layertools -jar application.jar extract
#
# ----------------------------------------------------------------------------------------
# Construction of our image using the result of our builder.
# According to your preferences. If AWS, then we recommend amazoncorretto.
#
#  FROM amazoncorretto:17
   FROM arm64v8/amazoncorretto:17
#
# Identification.
    ARG LABEL_TITLE="Please, provide a title."
    ARG LABEL_DESCRIPTION="Please, provide a description."
    ARG LABEL_CREATED="9999-99-99"
#
# Version : MAJOR.MINOR.REVISION-BUILD.
    ARG LABEL_VERSION_MAJOR="1"
    ARG LABEL_VERSION_MINOR="0"
    ARG LABEL_VERSION_REVISION="0"
    ARG LABEL_VERSION_BUILD="#1"
  LABEL app.image.title=${LABEL_TITLE}
  LABEL app.image.description=${LABEL_DESCRIPTION}
  LABEL app.image.created=${LABEL_CREATED}
  LABEL app.image.version.major=${LABEL_VERSION_MAJOR}
  LABEL app.image.version.minor=${LABEL_VERSION_MINOR}
  LABEL app.image.version.revison=${LABEL_VERSION_REVISION}
  LABEL app.image.version.build=${LABEL_VERSION_BUILD}
WORKDIR /application
   COPY --from=builder application/framework-dependencies/ ./
   COPY --from=builder application/spring-boot-loader/ ./
   COPY --from=builder application/corpo-dependencies/ ./
   COPY --from=builder application/snapshot-dependencies/ ./
   COPY --from=builder application/application/ ./
#
# ----------------------------------------------------------------------------------------
# Entry point.
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]
#
