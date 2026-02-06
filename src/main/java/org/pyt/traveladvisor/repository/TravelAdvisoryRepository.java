package org.pyt.traveladvisor.repository;

import org.pyt.traveladvisor.model.TravelAdvisory;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface TravelAdvisoryRepository
        extends ReactiveMongoRepository<TravelAdvisory, String> {
}