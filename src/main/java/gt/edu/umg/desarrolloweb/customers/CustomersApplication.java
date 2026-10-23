package gt.edu.umg.desarrolloweb.customers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the application.
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan.
 * - ComponentScan: finds every @RestController, @Service, @Repository... inside the
 *   scanned packages (customers + pagos).
 * - AutoConfiguration: because spring-boot-starter-web is on the classpath, Spring Boot
 *   starts an embedded Tomcat; because data-jpa + the MariaDB driver are there, it creates
 *   the DataSource and the EntityManager using the properties in application.yml.
 */
@SpringBootApplication(scanBasePackages = "gt.edu.umg.desarrolloweb")
@EntityScan(basePackages = "gt.edu.umg.desarrolloweb")
@EnableJpaRepositories(basePackages = "gt.edu.umg.desarrolloweb")
@EnableFeignClients(basePackages = "gt.edu.umg.desarrolloweb.pagos")
@EnableScheduling
public class CustomersApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomersApplication.class, args);
    }
}
