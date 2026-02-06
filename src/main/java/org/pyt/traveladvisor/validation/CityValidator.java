package org.pyt.traveladvisor.validation;

import org.pyt.traveladvisor.exception.ValidationException;
import org.springframework.stereotype.Component;

@Component
public class CityValidator {

    public void validate(String city) {

        if (city == null || city.isBlank()) {
            throw new ValidationException("City is required");
        }

        String normalized = city.trim();

        if (normalized.length() < 3) {
            throw new ValidationException("City name too short");
        }

        if (!normalized.matches("[a-zA-Z ]+")) {
            throw new ValidationException(
                    "City must contain only letters and spaces"
            );
        }
    }
}
