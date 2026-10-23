package gt.edu.umg.desarrolloweb.pagos.config;

import feign.RequestInterceptor;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Headers common to every bank call.
 *
 * The scenario can be forced with:
 *   mvn spring-boot:run -Dbank.simulation.scenario=ERROR_500
 * or through bank.simulation-scenario in application.yml.
 */
@Configuration
public class FeignConfig {

    private static final Logger log = LoggerFactory.getLogger(FeignConfig.class);
    private static final String SIMULATION_PROPERTY = "bank.simulation.scenario";

    @Value("${bank.api-key}")
    private String apiKey;

    @Value("${bank.simulation-scenario:}")
    private String configuredScenario;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            template.removeHeader("x-api-key");
            template.header("x-api-key", apiKey);
            template.removeHeader("X-Correlation-Id");
            template.header("X-Correlation-Id", UUID.randomUUID().toString());

            // The generated client sends AUTO by default; remove it so only our value remains.
            template.removeHeader("X-Simulation-Scenario");

            String scenario = System.getProperty(SIMULATION_PROPERTY);
            if (scenario == null || scenario.isBlank()) {
                scenario = configuredScenario;
            }
            if (scenario != null && !scenario.isBlank()) {
                String value = scenario.trim();
                log.info("[FEIGN] X-Simulation-Scenario={}", value);
                template.header("X-Simulation-Scenario", value);
            }
        };
    }
}
