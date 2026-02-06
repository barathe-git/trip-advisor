package org.pyt.traveladvisor.controller;

import lombok.RequiredArgsConstructor;
import org.pyt.traveladvisor.dto.AdvisoryResponseDto;
import org.pyt.traveladvisor.dto.AdvisoryWithAuditDto;
import org.pyt.traveladvisor.dto.ApiResponse;
import org.pyt.traveladvisor.mapper.AdvisoryMapper;
import org.pyt.traveladvisor.service.AdvisoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;

@RestController
@RequestMapping("/api/v1/advisories")
@RequiredArgsConstructor
public class AdvisoryController {

    private final AdvisoryService service;
    private final AdvisoryMapper mapper;

    @Value("${app.sync.multi-city-audit:true}")
    private boolean multiCityAudit;

    // ---------------- FETCH ----------------

    @GetMapping
    public Mono<ApiResponse<List<AdvisoryResponseDto>>> get(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country) {

        return service.fetch(city, country)
                .map(mapper::toDto)
                .collectList()
                .map(ApiResponse::success);
    }

    // ---------------- REFRESH ----------------

    @PostMapping("/refresh")
    public Mono<ApiResponse<?>> refresh(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country) {

        if (city != null) {
            return service.syncCityWithAudit(city)
                    .map(tuple -> ApiResponse.success(
                            List.of(mapper.toDto(tuple.getT1())),
                            tuple.getT2()
                    ));
        }

        // multi-city flow returns tuples with audit
        if (multiCityAudit) {
            return service.refresh(null, country)
                    .map(tuple -> new AdvisoryWithAuditDto(mapper.toDto(tuple.getT1()), tuple.getT2()))
                    .collectList()
                    .map(ApiResponse::success);
        }

        // when multiCityAudit disabled, return only DTOs
        return service.refresh(null, country)
                .map(Tuple2::getT1)
                .map(mapper::toDto)
                .collectList()
                .map(ApiResponse::success);
    }

    // ---------------- DELETE ----------------

    @DeleteMapping("/{city}")
    public Mono<ApiResponse<String>> deleteCity(@PathVariable String city) {

        return service.deleteCity(city)
                .thenReturn(ApiResponse.success("Deleted city: " + city));
    }

    // ---------------- SEARCH ----------------

    @GetMapping("/search")
    public Mono<ApiResponse<List<AdvisoryResponseDto>>> search(
            @RequestParam double min,
            @RequestParam double max) {

        return service.searchByTemp(min, max)
                .map(mapper::toDto)
                .collectList()
                .map(ApiResponse::success);
    }
}
