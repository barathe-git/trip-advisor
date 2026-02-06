package org.pyt.traveladvisor.client;

import lombok.RequiredArgsConstructor;
import org.pyt.traveladvisor.config.ExternalApiProperties;
import org.pyt.traveladvisor.dto.WeatherApiResponseDto;
import org.pyt.traveladvisor.model.WeatherInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OpenWeatherClient {

    private final WebClient weatherWebClient;
    private final ExternalApiProperties props;

    public Mono<WeatherApiResponseDto> fetchWeather(String city) {

        return weatherWebClient.get()
                .uri(uri -> uri
                        .path("/data/2.5/weather")
                        .queryParam("q", city)
                        .queryParam("APPID", props.getWeather().getApiKey())
                        .queryParam("units", "metric")
                        .build())
                .retrieve()
                .bodyToMono(WeatherApiResponseDto.class);
    }
}
