package org.pyt.traveladvisor.mapper;

import org.pyt.traveladvisor.dto.AdvisoryResponseDto;
import org.pyt.traveladvisor.model.TravelAdvisory;
import org.pyt.traveladvisor.service.AdvisoryEngine;
import org.springframework.stereotype.Component;

@Component
public class AdvisoryMapper {

    private final AdvisoryEngine engine;

    public AdvisoryMapper(AdvisoryEngine engine) {
        this.engine = engine;
    }

    public AdvisoryResponseDto toDto(TravelAdvisory adv) {

        AdvisoryResponseDto.WeatherDto weather =
                new AdvisoryResponseDto.WeatherDto(
                        adv.getWeather().getDescription(),
                        adv.getWeather().getTemperature(),
                        adv.getWeather().getFeelsLike(),
                        adv.getWeather().getHumidity(),
                        adv.getWeather().getWindSpeed(),
                        adv.getWeather().getSunrise(),
                        adv.getWeather().getSunset()
                );

        AdvisoryResponseDto.CountryDto country =
                new AdvisoryResponseDto.CountryDto(
                        adv.getCountry().getName(),
                        adv.getCountry().getCurrency(),
                        adv.getCountry().getCapital(),
                        adv.getCountry().getTimezones(),
                        adv.getCountry().getLanguages(),
                        adv.getCountry().getFlagUrl(),
                        adv.getCountry().getPopulation(),
                        adv.getCountry().getRegion()
                );

        AdvisoryResponseDto dto = new AdvisoryResponseDto(
                adv.getCity(),
                weather,
                country,
                adv.getSyncedAt()
        );

        dto.setAdvisory(engine.build(adv.getWeather()));

        return dto;
    }
}
