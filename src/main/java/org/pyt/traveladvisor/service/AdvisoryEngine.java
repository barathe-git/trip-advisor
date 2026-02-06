package org.pyt.traveladvisor.service;

import org.pyt.traveladvisor.model.WeatherInfo;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Component
public class AdvisoryEngine {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("hh:mm a");

    public String build(WeatherInfo w) {

        StringBuilder msg = new StringBuilder();

        // ---------------- TEMPERATURE ----------------

        if (w.getTemperature() < 5)
            msg.append("Very cold weather. Winter gear required. ");

        else if (w.getTemperature() < 15)
            msg.append("Cold conditions. Wear warm clothing. ");

        else if (w.getTemperature() < 25)
            msg.append("Pleasant temperature. Ideal for travel. ");

        else if (w.getTemperature() < 32)
            msg.append("Warm weather. Stay hydrated. ");

        else
            msg.append("Very hot weather. Avoid prolonged sun exposure. ");

        // ---------------- HUMIDITY ----------------

        if (w.getHumidity() > 70)
            msg.append("High humidity may feel uncomfortable. ");

        // ---------------- WIND ----------------

        if (w.getWindSpeed() > 8)
            msg.append("Windy conditions. Secure loose items. ");

        // ---------------- WEATHER DESCRIPTION ----------------

        String desc = w.getDescription().toLowerCase();

        if (desc.contains("rain"))
            msg.append("Carry an umbrella. ");

        if (desc.contains("snow"))
            msg.append("Snow conditions. Travel carefully. ");

        if (desc.contains("storm"))
            msg.append("Severe weather warning. Limit outdoor activity. ");

        if (desc.contains("clear"))
            msg.append("Clear skies — great for sightseeing. ");

        // ---------------- DAYLIGHT ----------------

        try {
            LocalTime sunrise =
                    LocalTime.parse(w.getSunrise(), FORMAT);

            LocalTime sunset =
                    LocalTime.parse(w.getSunset(), FORMAT);

            Duration daylight =
                    Duration.between(sunrise, sunset);

            if (daylight.toHours() < 10)
                msg.append("Short daylight hours — plan activities early. ");

        } catch (Exception ignored) {}

        return msg.toString().trim();
    }
}
