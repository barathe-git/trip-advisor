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

    @Data
    public static class Weather {
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class Country {
        private String baseUrl;
    }
}

