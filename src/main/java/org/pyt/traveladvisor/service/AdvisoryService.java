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
            log.info("[SERVICE] Fetching advisory from database for city: {}", city);
            String key = normalize(city);
            return repo.findById(key)
                    .flux()
                    .doOnNext(advisory -> log.debug("[SERVICE] Found advisory for city: {}", city))
                    .doOnComplete(() -> log.debug("[SERVICE] Completed fetching for city: {}", city));
        }

        if (country != null) {
            log.info("[SERVICE] Fetching advisories from database for country: {}", country);
            return repo.findAll()
                    .filter(a ->
                            a.getCountry().getName()
                                    .equalsIgnoreCase(country))
                    .doOnNext(advisory -> log.debug("[SERVICE] Found advisory for city: {} in country: {}", advisory.getCity(), country))
                    .doOnComplete(() -> log.info("[SERVICE] Completed fetching advisories for country: {}", country));
        }

        log.info("[SERVICE] Fetching all advisories from database");
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
        log.info("[SERVICE] Refreshing advisory for city: {}", key);

        return syncCityWithAudit(key)
                .flux()
                .doOnNext(tuple -> log.info("[SERVICE] Successfully refreshed city: {} with audit type: {}", city, tuple.getT2()))
                .onErrorResume(err -> {
                    log.error("[SERVICE] Failed syncing city: {}, error: {}", key, err.getMessage());
                    return Flux.empty();
                });
    }

    private Flux<Tuple2<TravelAdvisory, AuditType>> refreshCountry(String country, int concurrency) {
        String normalizedCountry = country.trim();
        log.info("[SERVICE] Refreshing advisories for country: {}", normalizedCountry);

        int topN = props.getCities().getTopN();

        Mono<Set<String>> storedCitiesMono = fetch(null, normalizedCountry)
                .map(TravelAdvisory::getCity)
                .map(this::normalize)
                .collect(Collectors.toSet())
                .doOnNext(cities -> log.info("[SERVICE] Found {} stored cities in database for country: {}", cities.size(), normalizedCountry));

        Mono<List<String>> topCitiesMono = getTopCitiesForCountry(normalizedCountry, topN)
                .doOnNext(cities -> log.info("[SERVICE] Fetched top {} cities for country: {}, cities: {}", cities.size(), normalizedCountry, cities));

        return Mono.zip(storedCitiesMono, topCitiesMono)
                .flatMapMany(tuple -> buildUnionAndSync(tuple, concurrency))
                .doOnComplete(() -> log.info("[SERVICE] Completed refreshing advisories for country: {}", normalizedCountry))
                .onErrorResume(err -> {
                    log.error("[SERVICE] Country refresh failed for: {}, error: {}", normalizedCountry, err.getMessage());
                    return Flux.empty();
                });
    }

    private Flux<Tuple2<TravelAdvisory, AuditType>> refreshAllCities(int concurrency) {
        log.info("[SERVICE] Refreshing all advisories from database");

        return repo.findAll()
                .map(TravelAdvisory::getCity)
                .map(this::normalize)
                .distinct()
                .doOnNext(city -> log.debug("[SERVICE] Syncing city: {}", city))
                .flatMap(this::syncCityWithAudit, concurrency)
                .doOnComplete(() -> log.info("[SERVICE] Completed refreshing all advisories"))
                .onErrorContinue((err, obj) ->
                        log.warn("[SERVICE] Failed syncing city: {}, error: {}", obj, err.getMessage()));
    }

    /**
     * Refreshes all cities in batches to handle large datasets efficiently.
     * Prevents memory overflow when dealing with millions of records.
     *
     * @param batchSize number of cities to process per batch
     * @param concurrency concurrent requests per batch
     * @return Flux of Tuple2 containing TravelAdvisory and AuditType
     */
    public Flux<Tuple2<TravelAdvisory, AuditType>> refreshAllCitiesInBatches(int batchSize, int concurrency) {
        log.info("[SERVICE] Starting batch refresh - batch size: {}, concurrency: {}", batchSize, concurrency);

        return repo.findAll()
                .map(TravelAdvisory::getCity)
                .map(this::normalize)
                .distinct()
                .buffer(batchSize)
                .doOnNext(batch -> log.info("[SERVICE] Processing batch of {} cities", batch.size()))
                .flatMap(batch ->
                        Flux.fromIterable(batch)
                                .doOnNext(city -> log.debug("[SERVICE] Syncing city in batch: {}", city))
                                .flatMap(this::syncCityWithAudit, concurrency)
                                .doOnComplete(() -> log.info("[SERVICE] Batch of {} cities completed", batch.size()))
                                .onErrorContinue((err, obj) ->
                                        log.warn("[SERVICE] Failed syncing city in batch: {}, error: {}", obj, err.getMessage())),
                        1  // Process batches sequentially to control memory usage
                )
                .doOnComplete(() -> log.info("[SERVICE] Completed batch refresh of all advisories"))
                .onErrorContinue((err, obj) ->
                        log.warn("[SERVICE] Batch processing error: {}", err.getMessage()));
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

    public Mono<List<String>> delete(String city, String country) {

        if (city != null) {
            log.info("[SERVICE] Deleting advisory for city: {}", city);
            String key = normalize(city);
            return repo.findById(key)
                    .doOnNext(advisory -> log.info("[SERVICE] Found advisory for city: {} in country: {}", city, advisory.getCountry().getName()))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("No data found for city: " + city)))
                    .flatMap(advisory -> repo.deleteById(key)
                            .doOnSuccess(v -> log.info("[SERVICE] Successfully deleted advisory for city: {}", city))
                            .then(Mono.just(List.of(city))));
        }

        if (country != null) {
            log.info("[SERVICE] Deleting advisories for country: {}", country);
            return fetch(null, country)
                    .map(TravelAdvisory::getCity)
                    .collectList()
                    .flatMap(cities -> {
                        if (cities.isEmpty()) {
                            log.warn("[SERVICE] No advisories found for country: {}", country);
                            return Mono.error(new IllegalArgumentException("No data found for country: " + country));
                        }
                        log.info("[SERVICE] Found {} advisories for country: {}, cities: {}", cities.size(), country, cities);
                        return fetch(null, country)
                                .map(TravelAdvisory::getCityKey)
                                .doOnNext(cityKey -> log.debug("[SERVICE] Deleting city key: {}", cityKey))
                                .flatMap(repo::deleteById)
                                .then(Mono.just(cities))
                                .doOnSuccess(deletedCities -> log.info("[SERVICE] Successfully deleted {} advisories for country: {}", deletedCities.size(), country));
                    });
        }

        return Mono.error(new IllegalArgumentException("Either city or country must be specified"));
    }

    // ---------------- SYNC SINGLE CITY ----------------

    public Mono<TravelAdvisory> syncCity(String city) {

        String key = city.trim().toLowerCase();
        log.info("[SERVICE] Syncing city: {}", city);

        return weatherClient.fetchWeather(city)
                .doOnNext(w ->
                        log.info("[SERVICE] Weather data received for city: {}, temp: {}°C", city, w.getMain().getTemp()))
                .flatMap(weather -> {

                    String code = weather.getSys().getCountry();
                    log.info("[SERVICE] Country code extracted for city: {}, code: {}", city, code);

                    return countryClient.getCountryByCode(code)
                            .map(country -> {
                                log.info("[SERVICE] Country data received for code: {}, country: {}", code, country.getName().getCommon());
                                return buildAdvisory(city, key, weather, country);
                            });
                })
                .doOnNext(a -> {
                    log.info("[SERVICE] Built advisory entity for city: {}", city);
                    log.debug("[SERVICE] Advisory details - city: {}, temp: {}°C, country: {}", a.getCity(), a.getWeather().getTemperature(), a.getCountry().getName());
                })
                .flatMap(advisory -> {
                    log.info("[SERVICE] Saving advisory to database for city: {}", city);
                    return repo.save(advisory)
                            .doOnNext(saved -> log.info("[SERVICE] Successfully saved advisory for city: {}", city));
                });
    }

    // ---------------- SYNC WITH AUDIT ----------------

    public Mono<Tuple2<TravelAdvisory, AuditType>> syncCityWithAudit(String city) {

        String key = city.trim().toLowerCase();
        log.info("[SERVICE] Syncing city with audit: {}", city);

        return repo.existsById(key)
                .doOnNext(exists -> log.info("[SERVICE] City exists in database: {}, exists: {}", city, exists))
                .flatMap(exists ->
                        syncCity(city)
                                .map(saved -> {
                                    AuditType type =
                                            exists ? AuditType.UPDATED
                                                    : AuditType.CREATED;

                                    log.info("[SERVICE] Audit - city: {}, auditType: {}, isnew: {}",
                                            city, type, !exists);

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
