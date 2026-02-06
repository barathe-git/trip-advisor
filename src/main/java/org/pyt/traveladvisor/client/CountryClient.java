package org.pyt.traveladvisor.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pyt.traveladvisor.dto.CountryApiResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class CountryClient {

    private final WebClient countryWebClient;
    private final org.pyt.traveladvisor.config.ExternalApiProperties props;

    public Mono<CountryApiResponseDto> getCountryByCode(String code) {
        long startTime = System.currentTimeMillis();
        String endpoint = "/v3.1/alpha/" + code;
        String url = props.getCountry().getBaseUrl();

        log.info("[EXTERNAL API] Calling REST Countries API - URL: {}{}, code: {}", url, endpoint, code);

        return countryWebClient.get()
                .uri(endpoint)
                .retrieve()
                .bodyToMono(CountryApiResponseDto[].class)
                .map(arr -> arr[0])
                .doOnNext(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[EXTERNAL API] REST Countries API Response - code: {}, country: {}, region: {}, duration: {}ms",
                            code,
                            response.getName().getCommon(),
                            response.getRegion(),
                            duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[EXTERNAL API] REST Countries API Error - code: {}, error: {}, duration: {}ms",
                            code,
                            error.getMessage(),
                            duration);
                });
    }

    public Mono<CountryApiResponseDto> getCountryByName(String name) {
        long startTime = System.currentTimeMillis();
        String endpoint = "/v3.1/name/" + name;
        String url = props.getCountry().getBaseUrl();

        log.info("[EXTERNAL API] Calling REST Countries API - URL: {}{}, name: {}", url, endpoint, name);

        return countryWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v3.1/name/{name}")
                        .queryParam("fullText", "true")
                        .build(name))
                .retrieve()
                .bodyToMono(CountryApiResponseDto[].class)
                .map(arr -> arr[0])
                .doOnNext(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("[EXTERNAL API] REST Countries API Response - name: {}, code: {}, region: {}, duration: {}ms",
                            name,
                            response.getCca2(),
                            response.getRegion(),
                            duration);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("[EXTERNAL API] REST Countries API Error - name: {}, error: {}, duration: {}ms",
                            name,
                            error.getMessage(),
                            duration);
                });
    }
}
