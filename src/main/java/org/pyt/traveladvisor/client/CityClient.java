package org.pyt.traveladvisor.client;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pyt.traveladvisor.config.ExternalApiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CityClient {

    private final WebClient citiesWebClient;
    private final ExternalApiProperties props;

    public Mono<List<String>> getTopCitiesByCountryCode(String countryCode, int limit) {

        if (!isConfigured()) {
            log.warn("GeoNames username missing — returning empty city list");
            return Mono.just(emptyList());
        }

        return fetchCitiesFromGeoNames(countryCode, limit)
                .map(this::extractCityNames)
                .doOnNext(list -> logResult(list, countryCode))
                .onErrorResume(err -> handleGeoNamesError(countryCode, err));
    }

    private boolean isConfigured() {
        String username = props.getCities().getUsername();
        return username != null && !username.isBlank();
    }

    private Mono<GeoNamesResponse> fetchCitiesFromGeoNames(String countryCode, int limit) {
        String username = props.getCities().getUsername();

        return citiesWebClient.get()
                .uri(uri -> uri
                        .path("/searchJSON")
                        .queryParam("country", countryCode)
                        .queryParam("featureClass", "P")
                        .queryParam("orderby", "population")
                        .queryParam("maxRows", limit)
                        .queryParam("username", username)
                        .build())
                .retrieve()
                .bodyToMono(GeoNamesResponse.class);
    }

    private List<String> extractCityNames(GeoNamesResponse resp) {
        if (resp == null || resp.getGeonames() == null || resp.getGeonames().isEmpty()) {
            return emptyList();
        }

        return resp.getGeonames().stream()
                .map(GeoName::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toList());
    }

    private void logResult(List<String> cities, String countryCode) {
        log.info("GeoNames returned {} cities for {}", cities.size(), countryCode);
    }

    private Mono<List<String>> handleGeoNamesError(String countryCode, Throwable err) {
        log.warn("GeoNames call failed for {}: {}", countryCode, err.getMessage());
        return Mono.just(emptyList());
    }

    private List<String> emptyList() {
        return List.of();
    }

    // ---------- DTOs ----------

    @Data
    private static class GeoNamesResponse {
        private List<GeoName> geonames;
        private Integer totalResultsCount;
    }

    @Data
    private static class GeoName {
        private String name;
        private Long population;
        private String countryCode;
    }
}

