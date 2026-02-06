package org.pyt.traveladvisor.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final ExternalApiProperties props;

    @Bean
    public WebClient weatherWebClient() {
        return WebClient.builder()
                .baseUrl(props.getWeather().getBaseUrl())
                .build();
    }

    @Bean
    public WebClient countryWebClient() {
        return WebClient.builder()
                .baseUrl(props.getCountry().getBaseUrl())
                .build();
    }

    @Bean
    public WebClient citiesWebClient() {
        return WebClient.builder()
                .baseUrl(props.getCities().getBaseUrl())
                .build();
    }
}
