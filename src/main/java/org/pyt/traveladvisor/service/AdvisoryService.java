package org.pyt.traveladvisor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pyt.traveladvisor.client.CountryClient;
import org.pyt.traveladvisor.client.OpenWeatherClient;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisoryService {

    private final TravelAdvisoryRepository repo;
    private final OpenWeatherClient weatherClient;
    private final CountryClient countryClient;
    private final CityValidator validator;

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

    public Flux<TravelAdvisory> refresh(String city, String country) {

        if (city != null) {
            log.info("Refreshing advisory for city={}", city);
            return syncCity(city).flux();
        }

        if (country != null) {
            log.info("Refreshing advisories for country={}", country);
            return fetch(null, country)
                    .flatMap(a -> syncCity(a.getCity()));
        }

        log.info("Refreshing all advisories");
        return repo.findAll()
                .flatMap(a -> syncCity(a.getCity()));
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

    // ---------------- NORMALIZATION ----------------

    private String normalize(String city) {

        validator.validate(city);
        return city.trim().toLowerCase();
    }
}
