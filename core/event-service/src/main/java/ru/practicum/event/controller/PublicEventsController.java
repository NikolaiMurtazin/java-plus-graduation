package ru.practicum.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.AnalyzerClient;
import ru.practicum.CollectorClient;
import ru.practicum.event.dto.event.EventFullDto;
import ru.practicum.event.dto.event.EventRecommendationDto;
import ru.practicum.event.dto.event.EventShortDto;
import ru.practicum.event.dto.event.PublicEventRequestParams;
import ru.practicum.event.enums.Sort;
import ru.practicum.event.service.EventService;
import ru.practicum.ewm.stats.proto.ActionTypeProto;
import ru.practicum.ewm.stats.proto.RecommendationsMessages;
import ru.practicum.exeption.WrongDateException;
import ru.practicum.request.feign.RequestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events")
public class PublicEventsController {
    private final EventService eventService;
    private final CollectorClient collectorClient;
    private final AnalyzerClient analyzerClient;
    private final RequestClient requestClient;

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
        return eventService.getAll(params);
    }

    @GetMapping("/{eventId}")
    public EventFullDto getById(@PathVariable("eventId") long eventId, @RequestHeader("X-EWM-USER-ID") long userId) {
        collectorClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_VIEW);
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

    @GetMapping("/recommendations")
    public List<EventRecommendationDto> getRecommendations(
            @RequestHeader("X-EWM-USER-ID") long userId,
            @RequestParam(name="maxResults", defaultValue="10") int maxResults) {
        var recStream = analyzerClient.getRecommendationsForUser(userId, maxResults);
        var recList = recStream.toList();

        List<EventRecommendationDto> result = new ArrayList<>();
        for (RecommendationsMessages.RecommendedEventProto rp : recList) {
            result.add(new EventRecommendationDto(rp.getEventId(), rp.getScore()));
        }
        return result;
    }

    @PutMapping("/events/{eventId}/like")
    public void likeEvent(@PathVariable Long eventId,
                          @RequestHeader("X-EWM-USER-ID") long userId) {

        if (!requestClient.userAttendedEvent(userId, eventId)) {
            throw new BadRequestException("Пользователь не присутствовал на мероприятии " + eventId);
        }

        collectorClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }
}
