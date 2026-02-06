package org.pyt.traveladvisor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdvisoryResponseDto {

    private String city;
    private WeatherDto weather;
    private CountryDto country;
    private Instant syncedAt;
    private String advisory;

    public AdvisoryResponseDto(
            String city,
            WeatherDto weather,
            CountryDto country,
            Instant syncedAt) {

        this.city = city;
        this.weather = weather;
        this.country = country;
        this.syncedAt = syncedAt;
    }


    // -------- WEATHER --------

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WeatherDto {

        private String description;
        private double temperature;
        private double feelsLike;
        private int humidity;
        private double windSpeed;
        private String sunrise;
        private String sunset;
    }

    // -------- COUNTRY --------

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CountryDto {

        private String name;
        private String currency;
        private String capital;
        private List<String> timezones;
        private Map<String, String> languages;
        private String flagUrl;
        private long population;
        private String region;
    }
}
