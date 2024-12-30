package ru.practicum.event.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.event.dto.event.EventRequestDto;
import ru.practicum.event.feign.EventClient;
import ru.practicum.event.service.EventService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/events")
public class InternalEventController implements EventClient {

    private final EventService eventService;

    @Override
    @GetMapping("/{eventId}/users/{userId}")
    public EventRequestDto getByEventIdAndUserId(@PathVariable long eventId, @PathVariable  long userId) {
        return eventService.getByIdAndInitiatorId(eventId, userId);
    }

    @Override
    @GetMapping("/{eventId}")
    public EventRequestDto getEventById(@PathVariable long eventId) {
        return eventService.getByIdForRequest(eventId);
    }
}
