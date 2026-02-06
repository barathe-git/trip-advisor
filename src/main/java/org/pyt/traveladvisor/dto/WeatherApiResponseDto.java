package org.pyt.traveladvisor.dto;

import lombok.Data;

import java.util.List;

@Data
public class WeatherApiResponseDto {

    private MainDto main;
    private List<WeatherDto> weather;
    private SysDto sys;
    private WindDto wind;
    private int timezone;

    @Data
    public static class MainDto {
        private double temp;
        private double feels_like;
        private int humidity;
    }

    @Data
    public static class WeatherDto {
        private String description;
    }

    @Data
    public static class SysDto {
        private long sunrise;
        private long sunset;
        private String country;
    }

    @Data
    public static class WindDto {
        private double speed;
    }
}
