package gt.edu.umg.desarrolloweb.pagos.config;

import feign.RequestInterceptor;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Headers common to every bank call.
 *
 * The x-api-key header is added by the interceptor generated from the OpenAPI contract
 * (org.openapitools.configuration.ApiKeyRequestInterceptor), configured through
 * bankpaymentssimulator.security.apiKeyAuth.key. Here we only add the tracing id and the
 * optional simulation scenario, so the key is never duplicated.
 */
@Configuration
public class FeignConfig {

    @Value("${bank.simulation-scenario:}")
    private String simulationScenario;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            template.header("X-Correlation-Id", UUID.randomUUID().toString());
            if (simulationScenario != null && !simulationScenario.isBlank()) {
                template.header("X-Simulation-Scenario", simulationScenario.trim());
            }
        };
    }
}
