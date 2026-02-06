package org.pyt.traveladvisor.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("advisories")
@Data
public class TravelAdvisory {

    @Id
    private String cityKey;

    private String city;

    private WeatherInfo weather;

    private CountryInfo country;

    private Instant syncedAt;

    @Indexed()
    private Instant createdAt;
}

