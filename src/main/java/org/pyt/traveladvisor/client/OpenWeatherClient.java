package org.pyt.traveladvisor.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pyt.traveladvisor.config.ExternalApiProperties;
import org.pyt.traveladvisor.dto.WeatherApiResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenWeatherClient {

    private final WebClient weatherWebClient;
    private final ExternalApiProperties props;

    public Mono<WeatherApiResponseDto> fetchWeather(String city) {
        long startTime = System.currentTimeMillis();
        String url = props.getWeather().getBaseUrl();

        log.info("[EXTERNAL API] Calling OpenWeather API - URL: {}/data/2.5/weather, city: {}", url, city);

        return weatherWebClient.get()
                .uri(uri -> uri
                        .path("/data/2.5/weather")
                        .queryParam("q", city)
                        .queryParam("APPID", props.getWeather().getApiKey())
                        .queryParam("units", "metric")
                        .build())
                .retrieve()
                .bodyToMono(WeatherApiResponseDto.class)
                .doOnNext(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[EXTERNAL API] OpenWeather API Response - city: {}, temp: {}°C, humidity: {}%, duration: {}ms",
                            city,
                            response.getMain().getTemp(),
                            response.getMain().getHumidity(),
                            duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[EXTERNAL API] OpenWeather API Error - city: {}, error: {}, duration: {}ms",
                            city,
                            error.getMessage(),
                            duration);
                });
    }
}
