package org.pyt.traveladvisor.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CountryApiResponseDto {

    private NameDto name;
    private List<String> capital;
    private List<String> timezones;
    private Map<String, String> languages;
    private FlagsDto flags;
    private Map<String, Object> currencies;
    private long population;
    private String region;

    @Data
    public static class NameDto {
        private String common;
    }

    @Data
    public static class FlagsDto {
        private String png;
    }
}
