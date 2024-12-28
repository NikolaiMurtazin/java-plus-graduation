package ru.practicum;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;


@FeignClient(name = "stats-server")
public interface StatClient {

    @PostMapping("/hit")
    EndpointHitDTO saveStats(@RequestBody EndpointHitDTO hitDto);

    @CircuitBreaker(name = "myService", fallbackMethod = "getStats")
    @GetMapping("/stats")
    List<ViewStatsDTO> getStats(@RequestParam("start") String start,
                                @RequestParam("end") String end,
                                @RequestParam(value = "uris", required = false, defaultValue = "") List<String> uris,
                                @RequestParam(value = "unique", defaultValue = "false") Boolean unique);

    default List<ViewStatsDTO> getStats() {
        return new ArrayList<>();
    }
}

