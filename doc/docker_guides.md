# Docker Guides

> Part of the **Kafka Engineering Guide** for `org-rd-fullstack-springboot-eda`. See the [project README](../README.md).

**Scope:** Docker image and usage instructions for the three embedded "sandbox" components — Kafka, Flink, and Hazelcast. The Docker image can be used in different ways depending on your needs.

## Table of Contents

* [Docker Image](#docker-image)
* [Docker to Run the Application](#docker-to-run-the-application)
* [Docker to Use as Development Tools](#docker-to-use-as-development-tools)
* [Docker alternative](#docker-alternative)
* [Sources & Further Reading](#sources--further-reading)

## Docker Image

The Docker image and its technical documentation can be found here:

* [Docker Hub](https://hub.docker.com/repositories/rdemers)
* [Dockerfile](../Dockerfile)
* [Dockerfile.eda-middleware](../Dockerfile.eda-middleware)

The Docker image **embeds** three infrastructure engines that would normally run as external services:

| Engine        | Embedded Form                                        | Role in the Project                                                                 |
| ------------- | ---------------------------------------------------- | ----------------------------------------------------------------------------------- |
| **Kafka**     | `EmbeddedKafkaKraftBroker` (KRaft, in-process)       | Event backbone: topics, DLTs, producers/consumers, and transactional semantics.     |
| **Flink**     | `MiniCluster` (in-process JobManager + TaskManagers) | Stream processing: jobs, slots, and metrics.                                        |
| **Hazelcast** | Embedded member (in-process)                         | Distributed state: `IMap` for the `PipelineContext` and the CP subsystem for locks. |

### Docker to Run the Application

To simply run the Docker image:

```bash
docker run -it \
  -p 8080:8080 \
  -p 8081:8081 \
  org-rd-fullstack/springboot-eda:unspecified
```

The application is then accessible through the exposed application and management ports.

### Docker to Use as Development Tools

The same Docker image can also be used as a development and testing environment for accessing the embedded Kafka, Flink, and Hazelcast components. To access these services directly from the host, enable **host networking** in Docker Desktop, as shown below:

![Docker-Network-Settings](./asserts/docker-network-settings.png)

Then run the image with host networking enabled:

```bash
docker run --network host -it \
  org-rd-fullstack/springboot-eda:unspecified
```

With host networking enabled, the container shares the host's network namespace, allowing development tools running on the host to access the services exposed by the application.

## Docker alternative

This project also includes a Dockerfile that can be used to build a Docker image containing only the external services: Kafka, PostgreSQL, and Hazelcast.

Construction file: [Dockerfile.eda-middleware](../Dockerfile.eda-middleware)

## Sources & Further Reading

* [Sandbox Guides](./sandbox_guides.md) — Application-level documentation for the Kafka, Flink, and Hazelcast sandboxes.