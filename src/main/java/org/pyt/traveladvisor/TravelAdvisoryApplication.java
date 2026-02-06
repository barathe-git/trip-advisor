package org.pyt.traveladvisor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TravelAdvisoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(TravelAdvisoryApplication.class, args);
    }
}
