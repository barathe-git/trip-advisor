package org.pyt.traveladvisor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeatherInfo {

    private String description;
    private double temperature;
    private double feelsLike;
    private int humidity;
    private double windSpeed;
    private String sunrise;
    private String sunset;
}
