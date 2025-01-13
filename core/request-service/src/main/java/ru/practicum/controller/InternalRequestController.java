package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.request.dto.EventCountByRequest;
import ru.practicum.request.feign.RequestClient;
import ru.practicum.service.RequestService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/requests")
public class InternalRequestController implements RequestClient {

    private final RequestService requestService;

    @Override
    @GetMapping("{eventId}")
    public Integer getCountConfirmedRequest(@PathVariable long eventId) {
        return requestService.getCountConfirmedRequest(eventId);
    }

    @Override
    @GetMapping
    public List<EventCountByRequest> getConfirmedRequest(@RequestBody List<Long> eventIds) {
        return requestService.getConfirmedRequest(eventIds);
    }

    @Override
    @GetMapping("{userId}/{eventId}")
    public boolean userAttendedEvent(@PathVariable long userId, @PathVariable long eventId) {
        return requestService.userAttendedEvent(userId, eventId);
    }
}
