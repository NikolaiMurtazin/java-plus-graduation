package ru.practicum.request.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.request.dto.EventCountByRequest;

import java.util.List;

@FeignClient(name = "request-service", path = "/internal/requests")
public interface RequestClient {

    @GetMapping("{eventId}")
    Integer getCountConfirmedRequest(@PathVariable long eventId);

    @GetMapping
    List<EventCountByRequest> getConfirmedRequest(@RequestParam("eventIds") List<Long> eventIds);
}
