# Spring Boot Application Lifecycle

When you start a Spring Boot application, a lot happens behind the scenes before your code is ready to execute or serve requests. Just like Spring beans, the application itself follows a **well-defined lifecycle**. Understanding the **Spring Boot application lifecycle** gives you better control over:

* application initialization,
* startup logic,
* and **graceful shutdown**.

This knowledge is especially valuable for building reliable, observable, and production-ready systems.

---

## Spring Boot Application Lifecycle (High-Level Overview)

When you run a Spring Boot application (for example via `SpringApplication.run()`), the following stages occur:

---

### 1. Bootstrap Phase

* A `SpringApplication` instance is created.
* Application listeners and initializers are discovered and registered.
* The application type (SERVLET, REACTIVE, or NONE) is determined.

*No `ApplicationContext` exists yet.*

---

### 2. Environment Preparation

* Configuration properties are loaded from:

  * `application.properties` / `application.yml`
  * environment variables
  * system properties
  * command-line arguments
* Active profiles (e.g. `dev`, `test`, `prod`) are resolved.

*The environment is ready, but beans are not created yet.*

---

### 3. ApplicationContext Creation

* The appropriate `ApplicationContext` implementation is created.
* Bean definitions are loaded and registered.
* No beans are instantiated at this stage.

---

### 4. ApplicationContext Refresh

This is the **core startup phase**:

* Beans are instantiated and dependencies are injected.
* Bean lifecycle callbacks are triggered (`@PostConstruct`, `InitializingBean`, etc.).
* `BeanPostProcessors` are applied.
* `CommandLineRunner` and `ApplicationRunner` beans are executed.

*At this point, the application is fully initialized.*

---

### 5. Application Ready

* `ApplicationReadyEvent` is published.
* Embedded servers (Tomcat, Jetty, Netty) are ready to accept requests.
* The application is now **fully up and running**.

---

### 6. Shutdown Phase

When the JVM shuts down (e.g. `CTRL+C`, SIGTERM in containers, or orchestrators like Kubernetes):

* `ContextClosedEvent` is published.
* Beans receive shutdown callbacks:
  * `@PreDestroy`
  * `DisposableBean.destroy()`
* `SmartLifecycle` beans are stopped.
* The `ApplicationContext` is closed.

*This enables a clean and graceful shutdown.*

---

## Application Lifecycle Hooks

Spring provides several mechanisms to hook into different lifecycle stages.

---

### Application Events

Spring publishes events throughout the application lifecycle. You can listen to them using `@EventListener`.

```java
@Component
public class MyAppEventsListener {

    @EventListener(ApplicationStartingEvent.class)
    public void onStart() {
        System.out.println("Application is starting.");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        System.out.println("Application is ready.");
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        System.out.println("Application is shutting down.");
    }
}
```

Ideal for reacting to **global application state changes**.

---

### CommandLineRunner and ApplicationRunner

These interfaces allow you to execute logic **after the context is refreshed**, but before the application is considered fully ready.

```java
@Component
public class StartupRunner implements CommandLineRunner {

    @Override
    public void run(String... args) {
        System.out.println("Running startup logic.");
    }
}
```

Note:

* `CommandLineRunner` receives raw `String[] args`.
* `ApplicationRunner` provides structured access via `ApplicationArguments`.

---

### SmartLifecycle

`SmartLifecycle` is useful for components that need **controlled startup and shutdown**, especially background processes.

```java
@Component
public class MyLifecycleBean implements SmartLifecycle {

    private boolean running = false;

    @Override
    public void start() {
        System.out.println("Custom lifecycle bean started.");
        running = true;
    }

    @Override
    public void stop() {
        System.out.println("Custom lifecycle bean stopped.");
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
```

Common use cases:

* message consumers
* schedulers
* long-running background tasks

---

### @PreDestroy / DisposableBean

Spring guarantees that cleanup logic is executed during shutdown.

```java
@Component
public class ShutdownHook {

    @PreDestroy
    public void cleanup() {
        System.out.println("Cleaning up resources before shutdown.");
    }
}
```

Recommended for:

* closing connections
* flushing buffers
* releasing file or network resources

---

## Lifecycle Hooks Comparison

| Hook Type                                 | When It Runs                        | Common Use Case                       |
| ----------------------------------------- | ----------------------------------- | ------------------------------------- |
| **ApplicationStartingEvent**              | Very early, before context creation | Logging setup, metrics initialization |
| **ApplicationReadyEvent**                 | After startup is complete           | Cache warm-up, external notifications |
| **CommandLineRunner / ApplicationRunner** | After context refresh               | Startup logic, data seeding           |
| **@PreDestroy / DisposableBean**          | Before shutdown                     | Resource cleanup                      |
| **SmartLifecycle**                        | Start/stop phase                    | Background processes management       |

---

## Conclusion

The Spring Boot application lifecycle ensures a smooth transition from **startup → running → shutdown**.

### Key takeaways

* Use **Application Events** to react to major lifecycle phases.
* Use **CommandLineRunner / ApplicationRunner** for startup logic.
* Use **@PreDestroy** or **SmartLifecycle** for graceful shutdown.
* Combine **bean lifecycle hooks** with **application lifecycle events** for maximum control.

By mastering these mechanisms, you can build Spring Boot applications that are **robust, predictable, and production-ready**.
