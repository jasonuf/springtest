package com.example.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TaskManagerApplication — Spring Boot Entry Point
 * ==================================================
 * This is the class that starts the entire application.
 * When you run `java -jar task-manager.jar` or `mvn spring-boot:run`,
 * the JVM calls this class's main() method first.
 *
 * @SpringBootApplication is a "meta-annotation" — a shortcut that combines
 * three separate annotations:
 *
 * 1. @Configuration
 *    Marks this class as a source of bean definitions (just like any other
 *    @Configuration class). You can define @Bean methods here, though for
 *    maintainability we put config in dedicated classes (SecurityConfig, etc.).
 *
 * 2. @EnableAutoConfiguration
 *    The "magic" of Spring Boot. Scans the classpath for well-known libraries
 *    and automatically configures them based on what it finds:
 *      - Found spring-boot-starter-web? Configure DispatcherServlet, Tomcat.
 *      - Found spring-boot-starter-data-jpa? Configure EntityManagerFactory, JPA.
 *      - Found H2 on classpath + no DataSource configured? Create an in-memory DB.
 *      - Found spring-boot-starter-actuator? Register health/metrics endpoints.
 *    Auto-configuration beans are defined in spring-boot-autoconfigure.jar and
 *    listed in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 *    You can inspect them to understand exactly what's being set up.
 *
 * 3. @ComponentScan
 *    Tells Spring to scan this package and all sub-packages for classes
 *    annotated with @Component, @Service, @Repository, @Controller, @RestController,
 *    @Configuration, etc. and register them as beans.
 *    Default scan base: the package of the annotated class (com.example.taskmanager).
 *    This is why all your classes must be in this package or a sub-package.
 *
 * KEEP THIS CLASS SIMPLE:
 * The entry point class should contain only the minimum needed to start the app.
 * Don't add bean definitions, business logic, or configuration here — it makes
 * the startup sequence harder to reason about and the class harder to test.
 *
 * HOW SPRING BOOT STARTS:
 * 1. JVM calls main()
 * 2. SpringApplication.run() creates the ApplicationContext (the Spring IoC container)
 * 3. @ComponentScan registers all beans
 * 4. @EnableAutoConfiguration configures frameworks
 * 5. All @Bean methods are called
 * 6. All @Autowired / constructor dependencies are injected
 * 7. Tomcat starts on port 8080 (from server.port in application.yml)
 * 8. The app is ready to serve requests
 */
@SpringBootApplication
public class TaskManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskManagerApplication.class, args);
    }
}
