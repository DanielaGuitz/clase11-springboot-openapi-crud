package gt.edu.umg.desarrolloweb.customers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the application.
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan.
 * - ComponentScan: finds every @RestController, @Service, @Repository... inside this
 *   package and its sub-packages. That is why this class lives in the ROOT package.
 * - AutoConfiguration: because spring-boot-starter-web is on the classpath, Spring Boot
 *   starts an embedded Tomcat; because data-jpa + the MariaDB driver are there, it creates
 *   the DataSource and the EntityManager using the properties in application.yml.
 */
@SpringBootApplication
public class CustomersApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomersApplication.class, args);
    }
}
