package ru.practicum;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


@FeignClient(name = "stat-server")
public interface StatFeignClient {

    @PostMapping("/hit")
    EndpointHitDTO saveStats(@RequestBody EndpointHitDTO hitDto);

    @GetMapping("/stats")
    List<ViewStatsDTO> getStats(@RequestParam("start") String start,
                                @RequestParam("end") String end,
                                @RequestParam(value = "uris", required = false, defaultValue = "") List<String> uris,
                                @RequestParam(value = "unique", defaultValue = "false") Boolean unique);
}

