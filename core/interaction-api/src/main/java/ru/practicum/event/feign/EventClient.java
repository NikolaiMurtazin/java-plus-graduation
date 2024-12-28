package ru.practicum.event.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.event.dto.event.EventRequestDto;

@FeignClient(name = "event-service", path = "/internal/events")
public interface EventClient {

    @GetMapping("/{eventId}/users/{userId}")
    EventRequestDto getByEventIdAndUserId(@PathVariable long eventId, @PathVariable long userId);

    @GetMapping("/{eventId}")
    EventRequestDto getEventById(@PathVariable long eventId);
}
