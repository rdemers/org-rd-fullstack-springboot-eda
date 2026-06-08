# org-rd-fullstack-springboot-eda

## Fullstack application with a Kafka/Flink/Hazelcast sandbox built on Spring Boot and Nuxt

This project provides a comprehensive sandbox environment for exploring the principles, patterns, and concepts involved in building resilient event-driven software systems. It highlights the full range of constraints, trade-offs, and challenges encountered when designing and implementing Event-Driven Architecture (EDA) components.

The platform leverages Spring Boot, Nuxt, Apache Maven, Kafka, Flink, Hazelcast, and Docker to build OCI-compliant application containers. It consists of a collection of microservices designed for deployment on AWS/EKS and includes a web application implemented according to Static Site Generation (SSG) principles.

**Note:** In this architecture, Spring Boot bundles the backend services and the web frontend into a single deployable artifact. While exposing the application's services, it also serves the frontend's static assets, effectively functioning as a lightweight content delivery layer.


![alt text](./doc/asserts/springboot-eda.gif "Springboot-EDA")

* Sources: [login.png](./doc/asserts/login.png), [welcome.png](./doc/asserts/welcome.png), [persons.png](./doc/asserts/persons.png), [products.png](./doc/asserts/products.png), [inventories.png](./doc/asserts/inventories.png), [report.png](./doc/asserts/report.png), [requests.png](./doc/asserts/requests.png), [hazelcast_dashboard.png](./doc/asserts/hazelcast_dashboard.png), [kafka_dashboard.png](./doc/asserts/kafka_dashboard.png), [flink_dashboard.png](./doc/asserts/flink_dashboard.png), [operations_dashboard.png](./doc/asserts/operations_dashboard.png), [about.png](./doc/asserts/about.png).

---

## Important

Building a web application that packages multiple SOA services (limited to Backend-for-Frontend services) into a single deployable artifact is neither inherently recommended nor discouraged; the decision ultimately depends on specific architectural requirements and trade-offs. When adopting this approach, SOA services should be strictly confined to Backend-for-Frontend (BFF) responsibilities.

For demonstration purposes, this project embeds Kafka, Flink, Hazelcast, and an HSQLDB database directly within the application environment. In production-grade architectures, these components should typically be deployed and managed as independent external services.

This project is intended exclusively for educational, experimentation, and demonstration purposes.

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

![alt text](./doc/asserts/Kafka-eda-facts.png "Kafka-EDA-Facts")

### Thematic Index

* [Architecture & Topic Design](./doc/architecture_and_topic_design.md)
* [Lifecycle & Operations](./doc/lifecycle_and_operations.md)
* [Reliability & Delivery Semantics](./doc/reliability_and_delivery_semantics.md)
* [Consumer Acknowledgement & Idempotency](./doc/consumer_acknowledgement_and_idempotency.md)
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
