package ru.practicum.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.StatServiceAdapter;
import ru.practicum.event.dto.event.EventFullDto;
import ru.practicum.event.dto.event.EventShortDto;
import ru.practicum.event.dto.event.PublicEventRequestParams;
import ru.practicum.event.enums.Sort;
import ru.practicum.event.service.EventService;
import ru.practicum.exeption.WrongDateException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events")
public class PublicEventsController {
    private final EventService eventService;

    private final StatServiceAdapter statServiceAdapter;

    @GetMapping()
    public List<EventShortDto> getEventsPublic(@RequestParam(value = "text", required = false) String text,
                                               @RequestParam(value = "categories", required = false) List<Long> categories,
                                               @RequestParam(value = "paid", required = false) Boolean paid,
                                               @RequestParam(value = "rangeStart", required = false)
                                               @DateTimeFormat(pattern = ("yyyy-MM-dd HH:mm:ss")) LocalDateTime rangeStart,
                                               @RequestParam(value = "rangeEnd", required = false)
                                               @DateTimeFormat(pattern = ("yyyy-MM-dd HH:mm:ss")) LocalDateTime rangeEnd,
                                               @RequestParam(value = "onlyAvailable", defaultValue = "false") Boolean onlyAvailable,
                                               @RequestParam(value = "sort", required = false) Sort sort,
                                               @RequestParam(value = "from", defaultValue = "0") int from,
                                               @RequestParam(value = "size", defaultValue = "10") int size,
                                               HttpServletRequest request) {


        Map<String, LocalDateTime> ranges = validDate(rangeStart, rangeEnd);
        PublicEventRequestParams params = PublicEventRequestParams.builder()
                .text(text)
                .categories(categories)
                .paid(paid)
                .rangeStart(ranges.get("rangeStart"))
                .rangeEnd(ranges.get("rangeEnd"))
                .onlyAvailable(onlyAvailable)
                .sort(sort)
                .from(from)
                .size(size)
                .build();
        sendStats(request);
        return eventService.getAll(params);
    }

    @GetMapping("/{eventId}")
    public EventFullDto getById(@PathVariable("eventId") long eventId, HttpServletRequest request) {
        sendStats(request);
        return eventService.getById(eventId);
    }

    private Map<String, LocalDateTime> validDate(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeEnd != null && rangeStart != null && rangeEnd.isBefore(rangeStart)) {
            throw new WrongDateException("Range end must be after range start");
        }
        LocalDateTime effectiveRangeStart = rangeStart != null ? rangeStart : LocalDateTime.now();
        LocalDateTime effectiveRangeEnd = rangeEnd != null ? rangeEnd : effectiveRangeStart.plusYears(200);

        return Map.of("rangeStart", effectiveRangeStart, "rangeEnd", effectiveRangeEnd);
    }

    private void sendStats(HttpServletRequest request) {
        statServiceAdapter.saveStats(request);
    }
}
