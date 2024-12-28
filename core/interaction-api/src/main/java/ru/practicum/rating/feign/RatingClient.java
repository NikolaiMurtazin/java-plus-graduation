package ru.practicum.rating.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.event.dto.event.EventRatingDto;

import java.util.List;

@FeignClient(name = "rating-service", path = "/internal/ratings")
public interface RatingClient {

    @GetMapping("/events/{eventId}/count")
    Integer getCountEventRating(@PathVariable long eventId);

    @GetMapping
    List<EventRatingDto> getEventsRating(@RequestParam("eventIds") List<Long> eventIds);
}
