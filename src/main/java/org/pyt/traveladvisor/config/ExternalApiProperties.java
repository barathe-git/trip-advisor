package org.pyt.traveladvisor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "external")
@Data
public class ExternalApiProperties {

    private Weather weather;
    private Country country;
    private Cities cities;

    @Data
    public static class Weather {
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class Country {
        private String baseUrl;
    }

    @Data
    public static class Cities {
        private String baseUrl;
        private String username; // GeoNames username
        private int topN = 5;
        private int concurrency = 5;
    }
}
