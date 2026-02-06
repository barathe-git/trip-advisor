package org.pyt.traveladvisor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CountryInfo {

    private String name;
    private String currency;
    private String capital;
    private List<String> timezones;
    private Map<String, String> languages;
    private String flagUrl;
    private long population;
    private String region;
}
