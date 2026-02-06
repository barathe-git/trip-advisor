package org.pyt.traveladvisor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pyt.traveladvisor.client.CityClient;
import org.pyt.traveladvisor.client.CountryClient;
import org.pyt.traveladvisor.client.OpenWeatherClient;
import org.pyt.traveladvisor.config.ExternalApiProperties;
import org.pyt.traveladvisor.dto.AuditType;
import org.pyt.traveladvisor.dto.CountryApiResponseDto;
import org.pyt.traveladvisor.dto.WeatherApiResponseDto;
import org.pyt.traveladvisor.model.CountryInfo;
import org.pyt.traveladvisor.model.TravelAdvisory;
import org.pyt.traveladvisor.model.WeatherInfo;
import org.pyt.traveladvisor.repository.TravelAdvisoryRepository;
import org.pyt.traveladvisor.util.TimeUtil;
import org.pyt.traveladvisor.validation.CityValidator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisoryService {

    private final TravelAdvisoryRepository repo;
    private final OpenWeatherClient weatherClient;
    private final CountryClient countryClient;
    private final CityClient cityClient;
    private final CityValidator validator;
    private final ExternalApiProperties props;

    // ---------------- FETCH ----------------

    public Flux<TravelAdvisory> fetch(String city, String country) {

        if (city != null) {
            log.info("Fetching advisory for city={}", city);
            String key = normalize(city);
            return repo.findById(key).flux();
        }

        if (country != null) {
            log.info("Fetching advisories for country={}", country);
            return repo.findAll()
                    .filter(a ->
                            a.getCountry().getName()
                                    .equalsIgnoreCase(country));
        }

        return repo.findAll();
    }

    // ---------------- REFRESH ----------------

    // Updated: returns tuples with audit info for multi-city flows
    public Flux<Tuple2<TravelAdvisory, AuditType>> refresh(String city, String country) {

        int concurrency = props.getCities().getConcurrency();

        if (city != null) {
            return refreshSingleCity(city);
        }

        if (country != null) {
            return refreshCountry(country, concurrency);
        }

        return refreshAllCities(concurrency);
    }

    private Flux<Tuple2<TravelAdvisory, AuditType>> refreshSingleCity(String city) {
        String key = normalize(city);
        log.info("Refreshing advisory for city={}", key);

        return syncCityWithAudit(key)
                .flux()
                .onErrorResume(err -> {
                    log.error("Failed syncing city {}: {}", key, err.getMessage());
                    return Flux.empty();
                });
    }

    private Flux<Tuple2<TravelAdvisory, AuditType>> refreshCountry(String country, int concurrency) {
        String normalizedCountry = country.trim();
        log.info("Refreshing advisories for country={}", normalizedCountry);

        int topN = props.getCities().getTopN();

        Mono<Set<String>> storedCitiesMono = fetch(null, normalizedCountry)
                .map(TravelAdvisory::getCity)
                .map(this::normalize)
                .collect(Collectors.toSet());

        Mono<List<String>> topCitiesMono = getTopCitiesForCountry(normalizedCountry, topN);

        return Mono.zip(storedCitiesMono, topCitiesMono)
                .flatMapMany(tuple -> buildUnionAndSync(tuple, concurrency))
                .onErrorResume(err -> {
                    log.error("Country refresh failed for {}: {}", normalizedCountry, err.getMessage());
                    return Flux.empty();
                });
    }

    private Flux<Tuple2<TravelAdvisory, AuditType>> refreshAllCities(int concurrency) {
        log.info("Refreshing all advisories");

        return repo.findAll()
                .map(TravelAdvisory::getCity)
                .map(this::normalize)
                .distinct()
                .flatMap(this::syncCityWithAudit, concurrency)
                .onErrorContinue((err, obj) ->
                        log.warn("Failed syncing city {}: {}", obj, err.getMessage()));
    }

    private Mono<List<String>> getTopCitiesForCountry(String countryName, int topN) {
        return countryClient.getCountryByName(countryName)
                .flatMap(dto -> fetchTopCitiesWithFallback(dto, topN))
                .onErrorResume(err -> {
                    log.warn("Failed to fetch top cities for {}: {}", countryName, err.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    private Mono<List<String>> fetchTopCitiesWithFallback(CountryApiResponseDto dto, int topN) {
        String code = dto.getCca2();
        List<String> fallback = safeCapitals(dto.getCapital());

        log.info("Country: {}, ISO Code: {}, Capitals: {}", dto.getName().getCommon(), code, fallback);

        if (code == null || code.isBlank()) {
            log.warn("No country code found for {}, using capitals only", dto.getName().getCommon());
            return Mono.just(fallback);
        }

        return cityClient.getTopCitiesByCountryCode(code, topN)
                .map(list -> list.isEmpty() ? fallback : list);
    }

    private Mono<Tuple2<TravelAdvisory, AuditType>> syncCityWithAuditSafely(String city) {
        return syncCityWithAudit(city)
                .onErrorResume(err -> {
                    log.warn("Failed syncing city {}: {}", city, err.getMessage());
                    return Mono.empty();
                });
    }

    // ---------------- DELETE ----------------

    public Mono<Void> deleteCity(String city) {

        String key = normalize(city);
        log.warn("Deleting advisory for city={}", city);

        return repo.deleteById(key);
    }

    // ---------------- SYNC SINGLE CITY ----------------

    public Mono<TravelAdvisory> syncCity(String city) {

        String key = city.trim().toLowerCase();
        log.info("Syncing city={}", city);

        return weatherClient.fetchWeather(city)
                .doOnNext(w ->
                        log.debug("Weather fetched for city={}", city))
                .flatMap(weather -> {

                    String code = weather.getSys().getCountry();

                    return countryClient.getCountryByCode(code)
                            .map(country ->
                                    buildAdvisory(city, key, weather, country));
                })
                .doOnNext(a ->
                        log.info("Saved advisory city={}", city))
                .flatMap(repo::save);
    }

    // ---------------- SYNC WITH AUDIT ----------------

    public Mono<Tuple2<TravelAdvisory, AuditType>> syncCityWithAudit(String city) {

        String key = city.trim().toLowerCase();

        return repo.existsById(key)
                .flatMap(exists ->
                        syncCity(city)
                                .map(saved -> {
                                    AuditType type =
                                            exists ? AuditType.UPDATED
                                                    : AuditType.CREATED;

                                    log.info("Audit: city={} type={}",
                                            city, type);

                                    return Tuples.of(saved, type);
                                })
                );
    }

    // ---------------- BUILD ENTITY ----------------

    private TravelAdvisory buildAdvisory(
            String city,
            String key,
            WeatherApiResponseDto weather,
            CountryApiResponseDto country) {

        TravelAdvisory adv = new TravelAdvisory();

        adv.setCityKey(key);
        adv.setCity(city);

        adv.setWeather(new WeatherInfo(
                weather.getWeather().get(0).getDescription(),
                weather.getMain().getTemp(),
                weather.getMain().getFeels_like(),
                weather.getMain().getHumidity(),
                weather.getWind().getSpeed(),
                TimeUtil.toReadable(weather.getSys().getSunrise(), weather.getTimezone()),
                TimeUtil.toReadable(weather.getSys().getSunset(), weather.getTimezone())
        ));

        String currency =
                country.getCurrencies().keySet().iterator().next();

        adv.setCountry(new CountryInfo(
                country.getName().getCommon(),
                currency,
                country.getCapital().get(0),
                country.getTimezones(),
                country.getLanguages(),
                country.getFlags().getPng(),
                country.getPopulation(),
                country.getRegion()
        ));

        adv.setSyncedAt(Instant.now());
        adv.setCreatedAt(Instant.now());

        return adv;
    }

    // ---------------- SEARCH ----------------

    public Flux<TravelAdvisory> searchByTemp(double min, double max) {

        return repo.findAll()
                .filter(a ->
                        a.getWeather().getTemperature() >= min &&
                                a.getWeather().getTemperature() <= max);
    }

    // ---------------- HELPER ----------------

    private String normalize(String city) {

        validator.validate(city);
        return city.trim().toLowerCase();
    }

    private List<String> safeCapitals(List<String> capitals) {
        return capitals == null ? Collections.emptyList() : capitals;
    }

    private Flux<Tuple2<TravelAdvisory, AuditType>> buildUnionAndSync(
            Tuple2<Set<String>, List<String>> tuple,
            int concurrency) {

        Set<String> union = new HashSet<>(tuple.getT1());

        tuple.getT2().stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .forEach(union::add);

        log.info("Syncing {} cities for country", union.size());

        return Flux.fromIterable(union)
                .flatMap(city -> syncCityWithAuditSafely(city), concurrency);
    }
}
