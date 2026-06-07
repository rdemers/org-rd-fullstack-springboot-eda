# org-rd-fullstack-springboot-eda

## Fullstack application with a Kafka/Flink/Hazelcast sandbox built on Spring Boot and Nuxt

This project provides a fully featured sandbox environment for experimenting with the principles and concepts involved in developing resilient event-driven software components. It outlines the complete set of constraints, trade-offs, and challenges associated with the design and implementation of EDA artifacts.

The project leverages Spring Boot, Nuxt, Apache Maven, Kafka, Flink, Hazelcast and Docker to build an OCI-compliant application container. It consists of a collection of microservices designed for deployment in AWS/EKS environments and includes a Nuxt-based web application (Vue/Vuetify) implemented according to Jamstack architectural principles.

**Note:** In this configuration, Spring Boot is used solely to serve static HTTP content, effectively acting as a lightweight CDN.

![alt text](./doc/asserts/springboot-eda.gif "Springboot-EDA")

* Sources: [login.png](./doc/asserts/login.png), [AppCRUD.png](./doc/asserts/appcrud.png), [AppPDF.png](./doc/asserts/apppdf.png),[Swagger.png](./doc/asserts/swagger.png)

---

## Important

Building a web application that packages SOA services (BFF only) into a single artifact is neither explicitly recommended nor forbidden; it requires architectural judgment. When adopting this approach, SOA services should be strictly limited to Backend-for-Frontend (BFF) responsibilities.

In this project, Kafka, Flink and Hazelcast services, as well as an HSQLDB database, are embedded. In a production-grade architecture, these components should preferably be provided as external services.

This project is intended solely for learning and demonstration purposes.

---

## Prerequisites

The following software must be installed on your workstation in order to build and run this project:

* [Node.js](https://nodejs.org/en)
* [Java SDK](https://www.oracle.com/java/technologies/downloads/)
* [Apache Maven](https://maven.apache.org/download.cgi)
* [Optional – Git or ZIP download](https://git-scm.com/downloads)
* [Optional – VS Code / IDE](https://code.visualstudio.com/download)
* [Optional – VS Code Plugin (Volar)](https://marketplace.visualstudio.com/items?itemName=Vue.volar)
* [Optional – Docker (for image build)](https://www.docker.com/products/docker-desktop/)

---

## Kafka Engineering Guide: Stream Processing and EDA Resilience

This guide compiles best practices for designing, developing, and operating robust Kafka consumers, particularly in containerized environments (EKS).

### Thematic Index

* [Architecture & Topic Design](./doc/architecture_and_topic_design.md)
* [Lifecycle & Operations](./doc/lifecycle_and_operations.md)
* [Reliability & Delivery Semantics](./doc/reliability_and_delivery_semantics.md)
* [Persistence & Transaction Patterns](./doc/persistence_and_transaction_patterns.md)
* [Governance & Observability](./doc/governance_and_observability.md)
* [Data Corroboration](./doc/data_corroboration.md)

Other useful information:

* [Sandbox Guides (Kafka / Flink / Hazelcast)](./doc/sandbox_guides.md)
* [Springboot Application Lifecycle](./doc/sba_lifecycle.md)
* [Datamesh/DataFabric](./doc/datamesh_datafabric.md)

---

## Spring Boot – Getting Started

```bash
mvn clean                                        # Remove compiled files and artifacts.
mvn test                                         # Compile and run all tests (Java side only).
mvn install -DskipTests                          # Build and package the application (Java and Nuxt).
mvn spring-boot:run                              # Start the Spring Boot application.

mvn wrapper:wrapper                              # Regenerate Maven wrapper files.
mvn dependency:sources                           # Download dependency sources.
mvn dependency:resolve -Dclassifier=javadoc      # Download dependency Javadocs.

mvn spring-boot:build-image                      # Build an OCI image using Paketo Buildpacks.
                                                 # Alternatively, use the Dockerfile for custom builds.

java -jar target/springboot-nuxt-unspecified.jar # Run the packaged JAR directly.

# Docker
docker build --no-cache .                        # Build an OCI image from the current directory.
docker build --no-cache -t org-rd-fullstack/springboot-nuxt:unspecified .
                                                 # Build and tag the Docker image.

docker run -it -p8080:8080 -p8081:8081 \
  org-rd-fullstack/springboot-nuxt:unspecified   # Run the Docker image with port mappings.

docker system prune -a                           # Remove unused Docker data (use with caution).
docker image ls                                  # List local Docker images.
docker rmi -f <imageID>                          # Force remove an image by ID.

# Image inspection
dive org-rd-fullstack/springboot-nuxt:unspecified
                                                 # Inspect image layers.
                                                 # See: https://github.com/wagoodman/dive.

# Spring Boot layer tools
java -Djarmode=layertools \
  -jar target/springboot-nuxt-unspecified.jar list
                                                 # List JAR layers.

java -Djarmode=layertools \
  -jar target/springboot-nuxt-unspecified.jar extract \
  --destination target/tmp
                                                 # Extract JAR layers to a directory.
```

## Nuxt4 - Getting started

```bash
cd src/frontend                                  # Navigate to the web application root.

npm i -D vuetify vite-plugin-vuetify             # Install Vuetify plugins for Nuxt.
npm i @mdi/font                                  # Install Material Design Icons.

npm cache clean --force                          # Clear the npm cache.
npm install                                      # Install project dependencies.
npm run dev                                      # Start the app with hot reloading.
npm run preview                                  # Preview a production build locally.
npm run build && npm run start                   # Build and start the production version.
npm run generate                                 # Generate the static site.

npx nuxi@latest upgrade                          # Upgrade Nuxt to the latest version.
npx nuxi cleanup                                 # Remove temporary files and directories.
npm outdated                                     # List outdated packages.

npm set registry=https://registry.npmjs.org/     # Set npm registry (useful behind a proxy).
npm config set strict-ssl false --global         # Disable strict SSL checks (not for production).

npm i nuxi                                       # Install the nuxi module (optional).
npx nuxi init frontend                           # Create a new Nuxt app in the "frontend" directory.
```

---

When the application is running, the following endpoints are available

* [Nuxt4 Web Application](http://localhost:8080/app)
* [Swagger UI (API testing)](http://localhost:8080/swagger-ui)
* [OpenAPI Specification](http://localhost:8080/v3/api-docs)
* [Actuator Endpoints](http://localhost:8081/actuator)
* [Info Probe](http://localhost:8081/actuator/info)
* [Health Probe](http://localhost:8081/actuator/health)
* [Liveness Probe](http://localhost:8081/actuator/health/liveness)
* [Readiness Probe](http://localhost:8081/actuator/health/readiness)
* [Prometheus Metrics](http://localhost:8081/actuator/prometheus)
* [Apache/Flink Overview](http://localhost:{port}/overview)
* [Apache/Flink Jobs Overview](http://localhost:{port}/jobs/overview)
* [Apache/Flink Job Details](http://localhost:{port}/jobs/{jobId})
* [Apache/Flink Job Exceptions](http://localhost:{port}/jobs/{jobId}/exceptions)
* [Apache/Flink Job Checkpoints](http://localhost:{port}/jobs/{jobId}/checkpoints)

---

## Conclusion

Enjoy experimenting!
