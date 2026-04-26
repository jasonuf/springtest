# How the Application Works
### A Ground-Up Explanation for Java Developers

---

## Table of Contents

1. [The Problem: Building an HTTP API in Raw Java](#1-the-problem)
2. [Maven: Managing Other People's Code](#2-maven)
3. [Tomcat: The HTTP Server](#3-tomcat)
4. [Annotations: Metadata That Code Can Read](#4-annotations)
5. [Spring Core: The Object Wiring Problem](#5-spring-core)
6. [Spring MVC: Connecting HTTP to Java Methods](#6-spring-mvc)
7. [Spring Boot: Making Everything Automatic](#7-spring-boot)
8. [A Request's Full Journey](#8-full-journey)

---

## 1. The Problem

Imagine your manager says: "Write a program that listens for HTTP requests and returns JSON." You know Java. You open a blank file. Where do you start?

An HTTP request is just **text sent over a network socket**. In raw form it looks like this:

```
POST /api/v1/tasks HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Content-Length: 45

{"title":"Learn Java","description":"Read docs"}
```

To handle this in pure Java, you would have to:

1. **Open a server socket** on port 8080 and wait for connections.
2. **Spawn a thread** for each incoming connection so multiple clients don't block each other.
3. **Read bytes** from the socket and parse them into structured data (method, path, headers, body).
4. **Route the request**: if the path is `/api/v1/tasks` and the method is `POST`, call one block of code; if it is `GET /api/v1/tasks/1`, call a different one.
5. **Deserialise the JSON body** into a Java object.
6. **Run your business logic**.
7. **Serialise the result** back to JSON.
8. **Write the HTTP response** bytes to the socket (status line, headers, blank line, body).
9. Handle malformed requests, missing routes, uncaught exceptions — all without crashing.

This is several thousand lines of infrastructure code before you write a single line of business logic. This is not hypothetical — this is what developers did before frameworks existed. Three technologies were invented to solve these exact problems: **Maven** (managing external code), **Tomcat** (handling the socket and HTTP protocol), and **Spring** (wiring Java objects together and connecting HTTP to your methods). Spring Boot is the fourth — it automates the setup of all three.

---

## 2. Maven

### The Problem It Solves

Your project needs libraries written by other people — a JSON parser, a database driver, a web server. Without a tool like Maven, you would:

1. Go to a website, download a `.jar` file.
2. Download all the JARs *that JAR depends on* (its own dependencies).
3. Download *those* JARs' dependencies. And so on.
4. Manually add every `.jar` to your project's classpath.
5. Repeat for every developer on your team, on every machine.

This is the **"dependency hell"** problem. It was a genuine crisis in Java development in the early 2000s. Maven solved it in 2004.

### What Maven Is

Maven is a **build tool** that does two things:

1. **Manages dependencies** — you declare what libraries you need; Maven downloads them and all their transitive dependencies automatically from the internet (Maven Central).
2. **Runs your build** — it compiles, tests, and packages your application using a standardised, repeatable process.

### The `pom.xml` File

`pom.xml` stands for **Project Object Model**. It is the single file that describes everything about your project. Let's read it like Java code.

**Declaring who you are:**

```xml
<groupId>com.example</groupId>
<artifactId>task-manager</artifactId>
<version>0.0.1-SNAPSHOT</version>
<packaging>jar</packaging>
```

Think of this like a class declaration. `groupId` is the package (your organisation's reverse domain), `artifactId` is the class name (your project's name), `version` is which version of this "class" you are publishing. `SNAPSHOT` means "in development, not a released version." `packaging` says the output should be a `.jar` file.

**Declaring what you need (dependencies):**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

This is like an `import` statement, but for entire libraries. Maven reads this and downloads the Spring Boot web starter JAR — and every JAR it depends on — into a local cache on your machine (`~/.m2/repository`). The next time you build, it reads from the cache instead of downloading again.

Notice there is no `<version>` here. That is because of the parent POM:

**The Parent POM (inheritance):**

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
</parent>
```

This works exactly like class inheritance in Java. The parent POM is a POM file published by the Spring team. Your `pom.xml` inherits from it. The parent POM pre-declares the versions of hundreds of Spring-related libraries, all tested to work together. When you declare a Spring dependency without a version, Maven looks up the version in the parent — so you get guaranteed-compatible versions without managing them yourself.

### The Build Lifecycle

When you run `mvn package`, Maven executes a fixed sequence of phases:

```
validate → compile → test → package → verify → install → deploy
```

Each phase does exactly what it says. You only specify the last phase you want; Maven runs everything before it automatically. The important ones:

- **`compile`** — runs `javac` on all `.java` files in `src/main/java/`. Output goes to `target/classes/`.
- **`test`** — compiles `src/test/java/` and runs all test classes.
- **`package`** — bundles `target/classes/` and all dependency JARs into a single executable fat-jar at `target/task-manager-0.0.1-SNAPSHOT.jar`.

The fat-jar is the key output. You can copy it to any machine that has Java installed and run it with `java -jar task-manager.jar` — no Maven, no other setup required. Everything the application needs is inside the jar.

---

## 3. Tomcat

### The Problem It Solves

You now have a way to manage code. But you still need something to handle the network — opening sockets, reading HTTP bytes, managing threads. **Apache Tomcat** is that thing. It is a Java web server that has been in production use since 1999.

### What Tomcat Does

Tomcat is responsible for everything below your application code:

```
Network Layer:    Opens a TCP socket on port 8080.
                  Accepts incoming connections.
                  Spawns threads from a thread pool to handle concurrent requests.

HTTP Layer:       Reads the raw bytes from the socket.
                  Parses them into the HTTP protocol (method, URL, headers, body).
                  Writes the raw bytes of the HTTP response back to the socket.

Servlet Layer:    Calls your code with the parsed request data.
                  Takes your response data and sends it back.
```

Your code never touches a socket. Tomcat handles all of that.

### The Servlet: The Contract Between Tomcat and Your Code

How does Tomcat know how to call your code? Through the **Servlet API** — a Java specification that defines a contract. A Servlet is a Java class that implements the `javax.servlet.Servlet` interface (or more commonly, extends `HttpServlet`). Tomcat knows how to call any class that follows this contract.

The core of the contract is simple:

```java
// This is what a raw Servlet looks like (you never write this in Spring)
public class MyServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // Tomcat calls this method when a POST request arrives.
        // HttpServletRequest wraps all the parsed HTTP request data.
        // HttpServletResponse lets you write your response.

        String body = request.getReader().lines().collect(joining());
        response.setStatus(201);
        response.setContentType("application/json");
        response.getWriter().write("{\"id\": 1}");
    }
}
```

`HttpServletRequest` and `HttpServletResponse` are Tomcat's gift to you — they hide all the byte-parsing and socket-writing so you work with a clean Java API.

### Embedded vs. Standalone Tomcat

Traditionally, Tomcat was a standalone server program you installed on a machine. You would deploy your application as a `.war` file into Tomcat's `webapps/` directory. This required separate installation and configuration of the server.

Spring Boot changed this. When you add `spring-boot-starter-web` to your `pom.xml`, you get Tomcat bundled *inside* your application. It is an **embedded Tomcat** — just a library on your classpath. Spring Boot starts it programmatically when your application starts. This is how `java -jar app.jar` starts a full HTTP server: the server is inside the jar.

The trade-off: you give up some flexibility (you can't easily deploy multiple apps to one Tomcat instance) in exchange for simplicity (one command starts your app; no server administration required). This is the right trade-off for modern microservice deployments.

---

## 4. Annotations: Metadata That Code Can Read

Before explaining Spring, you need to understand what annotations actually *do*, because Spring is almost entirely built on them.

### What an Annotation Is

You know that `@Override` is an annotation. When you write:

```java
@Override
public String toString() { return "hello"; }
```

The compiler checks that `toString()` actually overrides a method from a superclass. But `@Override` has no behaviour of its own — it is just a **label**. It attaches a piece of information to the method. The Java compiler reads that label and acts on it.

You can define your own annotation:

```java
public @interface MyTag {
    String value() default "default";
}
```

And use it:

```java
@MyTag("hello")
public class Foo { }
```

On its own, this does nothing. For the annotation to have any effect, something needs to **read it and act on it**. That reading happens in two ways:

### Way 1: At Compile Time (Annotation Processors)

Some tools process annotations while `javac` is running. MapStruct works this way. When it sees:

```java
@Mapper(componentModel = "spring")
public interface TaskMapper { ... }
```

MapStruct's annotation processor generates a new `.java` file (`TaskMapperImpl.java`) during compilation. By the time compilation finishes, the implementation already exists as bytecode. This is why MapStruct is so fast — there is no runtime overhead, the code is generated ahead of time.

### Way 2: At Runtime (Reflection)

Java's reflection API lets code inspect other code while the program is running. You can ask the JVM questions like:

```java
Class<?> clazz = TaskController.class;

// Is @RestController present on this class?
boolean isController = clazz.isAnnotationPresent(RestController.class);

// What methods does it have?
Method[] methods = clazz.getDeclaredMethods();

// What annotations does each method have?
for (Method m : methods) {
    GetMapping mapping = m.getAnnotation(GetMapping.class);
    if (mapping != null) {
        System.out.println("GET endpoint: " + mapping.value()[0]);
    }
}
```

**This is exactly what Spring does at startup.** Spring scans your classes, reads their annotations via reflection, and uses that information to set up the entire application. When Spring sees `@RestController`, it knows "this class has HTTP handler methods." When it sees `@GetMapping("/api/v1/tasks")`, it knows "calls to GET /api/v1/tasks should invoke this method."

Annotations are not magic. They are labels. Spring is the code that reads those labels and acts on them.

---

## 5. Spring Core: The Object Wiring Problem

### The Problem It Solves

Suppose you have these classes in our application:

```java
public class TaskController {
    private TaskService service;
    // needs a TaskService to work
}

public class TaskServiceImpl {
    private TaskRepository repository;
    private TaskMapper mapper;
    // needs a TaskRepository and TaskMapper to work
}

public class TaskRepository {
    // needs a DataSource (database connection pool) to work
}
```

To create a working `TaskController`, you would write:

```java
DataSource dataSource = createDataSource();           // connect to DB
TaskRepository repository = new TaskRepository(dataSource);
TaskMapper mapper = new TaskMapper();                 // MapStruct-generated
TaskServiceImpl service = new TaskServiceImpl(repository, mapper);
TaskController controller = new TaskController(service);
```

In a small app this is manageable. But a real application might have 200 classes, each with 3-5 dependencies. That is hundreds of lines of object-construction code that you have to update every time you add a new class or change a dependency. You also have to ensure that objects are created in the right order (DataSource before Repository before Service before Controller).

**Spring Core solves this with the IoC container.**

### The IoC Container: An Object Factory

IoC stands for **Inversion of Control** — a pattern where instead of your code creating its own dependencies, something external creates them and hands them over.

Spring's IoC container is called the **ApplicationContext**. Think of it as a smart factory that:

1. Finds all your classes that are annotated with Spring's component annotations.
2. Figures out their dependencies (by reading their constructors).
3. Creates them in the right order.
4. Wires them together.
5. Stores them for the lifetime of the application.

Objects managed by the ApplicationContext are called **beans**.

### Telling Spring What to Manage

You mark a class as a Spring-managed bean by annotating it with one of these:

```java
@Component    // generic bean — this class is managed by Spring
@Service      // same as @Component, but semantically "this is a service layer class"
@Repository   // same as @Component, but adds database exception translation
@Controller   // same as @Component, but for MVC controllers
@RestController // same as @Controller + @ResponseBody
```

These are all functionally equivalent for the purposes of the container — they all tell Spring "create one instance of this class and manage it." The different names are **semantic labels** that communicate intent to human readers, not to the compiler.

When Spring scans `TaskServiceImpl` and sees `@Service`, it does (conceptually):

```java
// Spring's internal logic (simplified)
Object instance = createInstance(TaskServiceImpl.class);
// But first: what does the constructor need?
// TaskServiceImpl(TaskRepository, TaskMapper)
// → find the TaskRepository bean, find the TaskMapper bean, pass them in
applicationContext.register("taskServiceImpl", instance);
```

Spring stores beans by type and name. When any other bean needs a `TaskService`, Spring looks up the bean that implements that interface (`TaskServiceImpl`) and injects it.

### Dependency Injection via Constructors

When Spring creates `TaskServiceImpl`, it reads its constructor:

```java
public TaskServiceImpl(TaskRepository taskRepository, TaskMapper taskMapper) {
    this.taskRepository = taskRepository;
    this.taskMapper = taskMapper;
}
```

Spring sees two parameters. It looks in the container for a bean of type `TaskRepository` and a bean of type `TaskMapper`. It finds them (because those classes are also annotated), creates them if not already created, and passes them to this constructor. **You never call `new TaskServiceImpl(...)` yourself.** Spring does it.

This is **constructor injection**, and it is the preferred style because:
- The dependencies are listed explicitly in the constructor signature — they cannot be null.
- You can instantiate the class in a test without Spring by just calling `new TaskServiceImpl(mockRepo, mockMapper)`.
- The fields can be `final` — immutable after construction.

### Why Controllers Depend on the Interface, Not the Implementation

In `TaskController`:

```java
private final TaskService taskService;  // <- interface type, not TaskServiceImpl

public TaskController(TaskService taskService) {
    this.taskService = taskService;
}
```

Spring looks for a bean that implements `TaskService`. It finds `TaskServiceImpl`. At runtime, `taskService` holds a reference to a `TaskServiceImpl` instance — but the controller does not know or care. It only calls methods on the `TaskService` interface.

This matters enormously for testing. In a `@WebMvcTest` (a controller test), Mockito creates a fake object that implements `TaskService` without any database or business logic. Spring injects that fake into the controller. The controller cannot tell the difference — it just calls `taskService.createTask(...)` and gets back whatever you told the mock to return.

---

## 6. Spring MVC: Connecting HTTP to Java Methods

### The Problem It Solves

You now have Tomcat (handles sockets and HTTP), Spring (manages Java objects). But how do HTTP requests get routed to the right Java method? That is Spring MVC's job.

### The DispatcherServlet: One Servlet to Rule Them All

Earlier you saw that Tomcat calls code through the Servlet API. Spring MVC registers exactly **one** Servlet with Tomcat: the `DispatcherServlet`. Every single HTTP request to your application goes through it.

The DispatcherServlet acts as a **front controller** — a single entry point that then dispatches each request to the right method:

```
HTTP Request
    │
    ▼
 Tomcat (reads bytes, parses HTTP, calls the Servlet)
    │
    ▼
 DispatcherServlet.service(request, response)
    │
    │   "Which method handles POST /api/v1/tasks?"
    ▼
 HandlerMapping (looks up: TaskController.createTask())
    │
    ▼
 HandlerAdapter (prepares arguments, calls the method)
    │   - Reads @RequestBody, calls Jackson to deserialise JSON
    │   - Validates the DTO with @Valid
    │   - Extracts @PathVariable from the URL
    ▼
 TaskController.createTask(TaskCreateRequest request)
    │
    ▼
 Return value handling
    │   - Takes the returned TaskResponse
    │   - Jackson serialises it to JSON bytes
    │   - Writes to HttpServletResponse
    ▼
 Tomcat (writes HTTP response bytes to the socket)
```

### How Routing Works

Spring MVC reads your controller class at startup and builds a routing table:

```java
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    @PostMapping        // → "POST /api/v1/tasks" maps here
    public ResponseEntity<TaskResponse> createTask(...) { ... }

    @GetMapping("/{id}") // → "GET /api/v1/tasks/{id}" maps here
    public ResponseEntity<TaskResponse> getTaskById(...) { ... }

    @DeleteMapping("/{id}") // → "DELETE /api/v1/tasks/{id}" maps here
    public ResponseEntity<Void> deleteTask(...) { ... }
}
```

`@RequestMapping("/api/v1/tasks")` sets the base path for all methods in the class. Method-level annotations (`@PostMapping`, `@GetMapping`, etc.) add the sub-path and HTTP method constraint. Spring combines them to build the full route.

When `POST /api/v1/tasks` arrives, the DispatcherServlet consults its routing table, finds `TaskController.createTask`, and calls it.

### How Method Arguments Are Resolved

Your controller methods can declare many different kinds of parameters, and Spring figures out how to populate each one:

```java
public ResponseEntity<TaskResponse> createTask(
    @Valid @RequestBody TaskCreateRequest request,  // ← JSON body → Java object
    @PathVariable Long id,                          // ← URL segment → Long
    @RequestParam String status,                    // ← ?status=TODO → String
    Pageable pageable,                              // ← ?page=0&size=20 → Pageable
    HttpServletRequest rawRequest                   // ← the raw Servlet request
) { ... }
```

For each parameter, Spring finds a **HandlerMethodArgumentResolver** — a class that knows how to produce a value of that type from the request. This is an extensible system; you can write your own argument resolvers.

The most important ones:

**`@RequestBody`**: Jackson reads the HTTP body bytes, parses them as JSON, and constructs the Java object. If `@Valid` is also present, Spring immediately runs bean validation on the result. If validation fails, Spring throws `MethodArgumentNotValidException` before your method body executes.

**`@PathVariable`**: Spring extracts the segment from the URL that matched the `{id}` placeholder in `@GetMapping("/{id}")` and converts it to the declared type (`Long`). If it cannot be converted, Spring throws `MethodArgumentTypeMismatchException`.

**`@RequestParam`**: Spring reads the `?key=value` query string.

**`Pageable`**: Spring's `PageableHandlerMethodArgumentResolver` builds a `Pageable` object from `?page=0&size=20&sort=createdAt,desc` query parameters.

### ResponseEntity: Full Control Over the HTTP Response

When your method returns `ResponseEntity<TaskResponse>`, you are building the HTTP response explicitly:

```java
return ResponseEntity.created(location).body(created);
//     ^                ^                ^
//     |                |                |
//     HTTP 201 status  Location header  Response body (serialised to JSON by Jackson)
```

Spring takes this `ResponseEntity`, calls Jackson to serialise the body to JSON bytes, writes the status code, adds the headers, and hands everything to Tomcat to send back over the socket.

### Jackson: Java ↔ JSON

Jackson is the library that handles serialisation and deserialisation. It is included automatically by `spring-boot-starter-web`.

**Deserialisation (JSON → Java):**

```json
{"title": "Learn Java", "description": "Read the docs"}
```
↓ Jackson reads field names and finds matching fields/setters/constructor parameters in `TaskCreateRequest` ↓
```java
new TaskCreateRequest("Learn Java", "Read the docs")
```

For Java records, Jackson uses the canonical constructor (the one with all components as parameters).

**Serialisation (Java → JSON):**

```java
new TaskResponse(1L, "Learn Java", "Read the docs", TaskStatus.TODO, ...)
```
↓ Jackson reads the record's accessor methods (`id()`, `title()`, `description()`, ...) ↓
```json
{"id":1,"title":"Learn Java","description":"Read the docs","status":"TODO",...}
```

Jackson's behaviour is configured in `application.yml`:
```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false  # LocalDateTime → "2024-01-15T10:30:00", not [2024,1,15,10,30,0]
```

---

## 7. Spring Boot: Making Everything Automatic

You now understand all the pieces. The question is: who sets them all up?

Before Spring Boot (pre-2014), you would have to:
- Write XML configuration files declaring every bean.
- Configure the DispatcherServlet manually in `web.xml`.
- Create a DataSource bean with database connection details.
- Create an EntityManagerFactory for JPA.
- Start Tomcat separately and deploy a WAR file into it.
- Write dozens of `@Bean` methods configuring Jackson, Hibernate, etc.

This was hundreds of lines of configuration for every project, mostly identical from one project to the next. Spring Boot eliminated almost all of it.

### `@SpringBootApplication`

This single annotation on your main class does three things:

```java
@SpringBootApplication   // = @Configuration + @ComponentScan + @EnableAutoConfiguration
public class TaskManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskManagerApplication.class, args);
    }
}
```

**`@ComponentScan`** tells Spring to scan the package of this class (`com.example.taskmanager`) and all sub-packages for classes annotated with `@Component`, `@Service`, `@Repository`, `@Controller`, etc. It finds all your beans automatically. You never write a list of "here are my classes" — Spring discovers them.

**`@Configuration`** marks this class as a source of `@Bean` definitions. You can add methods here that create Spring beans (though we keep those in separate config classes for organisation).

**`@EnableAutoConfiguration`** is the heart of Spring Boot. It triggers the auto-configuration mechanism, described next.

### Auto-Configuration: Reading the Classpath

Auto-configuration is the process by which Spring Boot configures your application based on **what libraries are present on the classpath**. It is a sophisticated version of "if library X is present, then configure Y."

Spring Boot ships with ~130 auto-configuration classes. Each one has a condition. For example:

- `DataSourceAutoConfiguration` — condition: `HikariCP` is on the classpath AND a database URL is configured. Action: create a `DataSource` bean connected to that URL.
- `HibernateJpaAutoConfiguration` — condition: `DataSource` bean exists AND Hibernate is on the classpath. Action: create an `EntityManagerFactory`, configure Hibernate, start schema DDL.
- `DispatcherServletAutoConfiguration` — condition: `spring-webmvc` is on the classpath. Action: create and register the `DispatcherServlet`.
- `EmbeddedWebServerFactoryCustomizerAutoConfiguration` — condition: Tomcat is on the classpath. Action: create an embedded Tomcat instance on port 8080.

When you add `spring-boot-starter-web` to `pom.xml`, Maven downloads Spring MVC, Tomcat, Jackson, and their dependencies — all of which are now on the classpath. Spring Boot's auto-configuration detects them all and sets up the full web stack automatically.

When you add `spring-boot-starter-data-jpa` and the H2 driver, auto-configuration detects them and configures JPA with H2 as the database — because you declared a `spring.datasource.url` for H2 in `application.yml`.

You can see every auto-configuration class that ran by adding `--debug` to your run command and looking for the "CONDITIONS EVALUATION REPORT" in the startup logs.

### `SpringApplication.run()`

This is the single statement that starts everything:

```java
SpringApplication.run(TaskManagerApplication.class, args);
```

In sequence, it:

1. Creates a `SpringApplication` instance.
2. Detects that this is a web application (Tomcat is on the classpath).
3. Creates the `ApplicationContext` (the IoC container).
4. Triggers `@ComponentScan` — discovers all `@Service`, `@Repository`, `@Controller`, etc. classes.
5. Triggers `@EnableAutoConfiguration` — runs all relevant auto-configuration classes (sets up JPA, DispatcherServlet, etc.).
6. Creates all beans in the correct dependency order, injecting constructor parameters.
7. Starts embedded Tomcat on port 8080.
8. Registers the `DispatcherServlet` with Tomcat.
9. Logs "Started TaskManagerApplication in 3.2 seconds."
10. Blocks, waiting for HTTP requests.

### `application.yml`: Externalised Configuration

Spring Boot reads `src/main/resources/application.yml` at startup and makes every value available to the running application. Any value in this file can be overridden by:

- A profile-specific file (`application-postgres.yml` when the `postgres` profile is active).
- An environment variable (`SPRING_DATASOURCE_URL` overrides `spring.datasource.url`).
- A command-line argument (`--server.port=9090`).

This hierarchy means the same compiled jar can run as a development server against H2, a staging server against a test database, and a production server against RDS — all without recompilation. The jar is a fixed artefact; configuration is injected at runtime.

---

## 8. A Request's Full Journey

Let's trace exactly what happens when a client sends:

```
POST /api/v1/tasks HTTP/1.1
Content-Type: application/json

{"title":"Learn Spring Boot","description":"Read the docs"}
```

### Step 1: The Network

Tomcat's acceptor thread is blocked waiting on port 8080. The incoming TCP connection wakes it up. Tomcat picks a thread from its thread pool and assigns the connection to it.

### Step 2: HTTP Parsing

The assigned thread reads bytes from the socket. Tomcat parses them:
- Method: `POST`
- Path: `/api/v1/tasks`
- Header: `Content-Type: application/json`
- Body: `{"title":"Learn Spring Boot","description":"Read the docs"}`

Tomcat wraps this into an `HttpServletRequest` object.

### Step 3: Security Filter Chain

Before the request reaches the DispatcherServlet, it passes through Spring Security's filter chain. Our `SecurityConfig` says `anyRequest().permitAll()`, so the request passes through immediately. (When OAuth2 is enabled, this step validates the `Authorization: Bearer <token>` header.)

### Step 4: DispatcherServlet Routes the Request

The `DispatcherServlet` receives the `HttpServletRequest`. It consults its routing table:
- Method: `POST`, Path: `/api/v1/tasks`
- Match found: `TaskController.createTask()`

### Step 5: Argument Resolution

The DispatcherServlet needs to call `createTask(@Valid @RequestBody TaskCreateRequest request)`. It needs a `TaskCreateRequest` argument.

- `@RequestBody` → call Jackson with the body bytes and the target type `TaskCreateRequest`.
- Jackson parses the JSON, finds components `title` and `description`, calls `new TaskCreateRequest("Learn Spring Boot", "Read the docs")`.
- `@Valid` → run bean validation on the `TaskCreateRequest`. `title` is not blank. `description` length is within bounds. Validation passes.

### Step 6: Controller Method Executes

```java
public ResponseEntity<TaskResponse> createTask(TaskCreateRequest request) {
    TaskResponse created = taskService.createTask(request);
    // ...
}
```

`taskService` is the `TaskServiceImpl` bean that Spring injected into this controller at startup. The method call crosses the `TaskService` interface boundary and lands in `TaskServiceImpl.createTask()`.

### Step 7: Transaction Begins

`TaskServiceImpl` is annotated with `@Transactional`. Spring wraps the method call in a proxy. Before the actual method body runs, Spring's transaction proxy asks HikariCP for a database connection from the pool and begins a JDBC transaction (`connection.setAutoCommit(false)`).

### Step 8: Service Logic Executes

```java
Task task = taskMapper.toEntity(request);   // MapStruct-generated code runs
task.setStatus(TaskStatus.TODO);
Task saved = taskRepository.save(task);
```

`taskMapper.toEntity()` calls the MapStruct-generated `TaskMapperImpl.toEntity()` — plain Java code that was generated at compile time:

```java
// Generated by MapStruct, in target/generated-sources/annotations/
Task toEntity(TaskCreateRequest request) {
    Task task = new Task();
    task.setTitle(request.title());
    task.setDescription(request.description());
    return task;
}
```

`taskRepository.save(task)` calls Spring Data's generated repository implementation. Spring Data's implementation calls Hibernate's `EntityManager.persist(task)`.

### Step 9: JPA and Hibernate

Hibernate inspects the `Task` object, calls the `@PrePersist` method (which sets `createdAt`, `updatedAt`, and the default `status`), and generates the SQL:

```sql
INSERT INTO tasks (title, description, status, created_at, updated_at)
VALUES ('Learn Spring Boot', 'Read the docs', 'TODO', '2024-01-15T10:30:00', '2024-01-15T10:30:00')
```

H2 (or PostgreSQL in production) executes the INSERT and returns the generated ID. Hibernate reads it back and sets `task.id = 1`.

### Step 10: Transaction Commits

The service method returns. Spring's transaction proxy detects normal return (no exception). It calls `connection.commit()`. The row is now permanently written to the database. The connection is returned to HikariCP's pool.

### Step 11: Back in the Controller

```java
URI location = ServletUriComponentsBuilder
    .fromCurrentRequest()
    .path("/{id}")
    .buildAndExpand(created.id())
    .toUri();
// location = http://localhost:8080/api/v1/tasks/1

return ResponseEntity.created(location).body(created);
```

The controller builds a `ResponseEntity` with:
- HTTP status: `201 Created`
- Header: `Location: http://localhost:8080/api/v1/tasks/1`
- Body: a `TaskResponse` record

### Step 12: Serialisation

Spring MVC's return value handler sees the `ResponseEntity<TaskResponse>`. It calls Jackson to serialise the `TaskResponse`:

```java
// TaskResponse is a Java record, Jackson calls the accessor methods:
{"id":1,"title":"Learn Spring Boot","description":"Read the docs","status":"TODO",
 "createdAt":"2024-01-15T10:30:00","updatedAt":"2024-01-15T10:30:00"}
```

### Step 13: Writing the Response

The DispatcherServlet writes to the `HttpServletResponse` object:
- Sets status code 201.
- Sets `Content-Type: application/json`.
- Sets `Location: http://localhost:8080/api/v1/tasks/1`.
- Writes the JSON bytes to the response body.

Tomcat takes the `HttpServletResponse`, formats it as an HTTP response, and writes the bytes to the TCP socket.

### Step 14: The Client Receives

```
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/tasks/1
Content-Type: application/json

{"id":1,"title":"Learn Spring Boot",...}
```

The thread is returned to Tomcat's thread pool, ready to handle the next request.

---

## Summary

| Technology | Problem Solved | Mechanism |
|---|---|---|
| **Maven** | Managing external JARs and building the project | `pom.xml` declares dependencies; Maven downloads them and runs the build lifecycle |
| **Tomcat** | Opening sockets, parsing HTTP, managing threads | Implements the Servlet API; handles all network I/O |
| **Spring Core** | Wiring objects together | Scans annotations via reflection; creates beans; injects constructor arguments |
| **Spring MVC** | Routing HTTP requests to Java methods | DispatcherServlet receives all requests; resolves arguments; serialises responses via Jackson |
| **Spring Boot** | Setting up all of the above automatically | Detects classpath contents; runs auto-configuration; starts embedded Tomcat; reads `application.yml` |

The sequence when you run `java -jar app.jar`:

```
SpringApplication.run()
    → ComponentScan finds beans
    → Auto-configuration sets up Tomcat, JPA, MVC, Jackson, Security
    → All beans created and wired together
    → Tomcat starts listening on port 8080
    → DispatcherServlet registered with Tomcat
    → Application ready

On each HTTP request:
    Tomcat thread → Security filter → DispatcherServlet
    → Route lookup → Argument resolution (Jackson)
    → Validation → Controller method
    → Service method (inside a transaction)
    → Repository (Hibernate → SQL → Database)
    → Return value → Jackson serialisation
    → HTTP response → Tomcat → Socket → Client
```

Every annotation in this codebase is a label on a Java class or method. Spring reads those labels at startup via reflection and uses them to build this entire flow automatically.
