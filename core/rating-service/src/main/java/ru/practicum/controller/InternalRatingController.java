package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.event.dto.event.EventRatingDto;
import ru.practicum.rating.feign.RatingClient;
import ru.practicum.service.RatingService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/ratings")
public class InternalRatingController implements RatingClient {

    private final RatingService ratingService;

    @Override
    @GetMapping("/events/{eventId}/count")
    public Integer getCountEventRating(@PathVariable long eventId) {
        return ratingService.getCountEventRating(eventId);
    }

    @Override
    @GetMapping
    public List<EventRatingDto> getEventsRating(@RequestBody List<Long> eventIds) {
        return ratingService.getEventsRating(eventIds);
    }
}
