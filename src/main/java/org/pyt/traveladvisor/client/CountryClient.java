package org.pyt.traveladvisor.client;


import lombok.RequiredArgsConstructor;
import org.pyt.traveladvisor.dto.CountryApiResponseDto;
import org.pyt.traveladvisor.model.CountryInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CountryClient {

    private final WebClient countryWebClient;

    public Mono<CountryApiResponseDto> getCountryByCode(String code) {

        return countryWebClient.get()
                .uri("/v3.1/alpha/" + code)
                .retrieve()
                .bodyToMono(CountryApiResponseDto[].class)
                .map(arr -> arr[0]);
    }

    public Mono<CountryApiResponseDto> getCountryByName(String name) {
        return countryWebClient.get()
                .uri(uriBuilder -> uriBuilder.path("/v3.1/name/{name}")
                        .queryParam("fullText", "true")
                        .build(name))
                .retrieve()
                .bodyToMono(CountryApiResponseDto[].class)
                .map(arr -> arr[0]);
    }
}
