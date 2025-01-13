package ru.practicum.event.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.AnalyzerClient;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.event.dto.event.AdminEventRequestParams;
import ru.practicum.event.dto.event.EventRequestDto;
import ru.practicum.event.enums.EventAction;
import ru.practicum.event.dto.event.EventFullDto;
import ru.practicum.event.dto.event.EventShortDto;
import ru.practicum.event.dto.event.NewEventDto;
import ru.practicum.event.dto.event.PrivateEventParams;
import ru.practicum.event.dto.event.PublicEventRequestParams;
import ru.practicum.event.dto.event.UpdateEventAdminRequest;
import ru.practicum.event.dto.event.UpdateEventUserRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.enums.EventState;
import ru.practicum.event.model.QEvent;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.ewm.stats.proto.RecommendationsMessages;
import ru.practicum.exeption.ConflictException;
import ru.practicum.exeption.NotFoundException;
import ru.practicum.location.mapper.LocationMapper;
import ru.practicum.location.model.Location;
import ru.practicum.location.repository.LocationRepository;
import ru.practicum.request.dto.EventCountByRequest;
import ru.practicum.request.feign.RequestClient;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.feign.UserClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.practicum.event.enums.Sort.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final AnalyzerClient analyzerClient;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final UserClient userClient;
    private final RequestClient requestClient;
    private final EventMapper eventMapper;
    private final LocationMapper locationMapper;

    @Override
    public List<EventShortDto> getAll(PublicEventRequestParams params) {
        QEvent event = QEvent.event;
        List<BooleanExpression> conditions = new ArrayList<>();

        int from = params.getFrom();
        int size = params.getSize();

        conditions.add(event.state.eq(EventState.PUBLISHED));

        conditions.add(event.eventDate.after(params.getRangeStart()));
        conditions.add(event.eventDate.before(params.getRangeEnd()));
        if (params.getText() != null) {
            conditions.add(event.description.containsIgnoreCase(params.getText()).or(event.annotation.containsIgnoreCase(params.getText())));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            conditions.add(event.category.id.in(params.getCategories()));
        }
        if (params.getPaid() != null) {
            conditions.add(event.paid.eq(params.getPaid()));
        }
        BooleanExpression finalConditional = conditions.stream().reduce(BooleanExpression::and).get();

        PageRequest pageRequest = PageRequest.of(from > 0 ? from / size : 0, size);

        List<Event> events = eventRepository.findAll(finalConditional, pageRequest).getContent();
        if (events.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Integer> eventLimitById = events.stream().collect(Collectors.toMap(Event::getId, Event::getParticipantLimit));

        List<EventCountByRequest> eventsIdWithConfirmedRequest = requestClient.getConfirmedRequest(List.copyOf(eventLimitById.keySet()));
        if (params.getOnlyAvailable()) {
            eventsIdWithConfirmedRequest = eventsIdWithConfirmedRequest.stream()
                    .filter(ev -> ev.getCount() >= getFinalEvent(ev, events).getParticipantLimit())
                    .toList();
        }

        Map<Long, UserShortDto> initiatorsByEventId = getInitiators(events);

        List<EventShortDto> eventShortDtos = new ArrayList<>(eventsIdWithConfirmedRequest.stream()
                .map(ev -> {
                    Event finalEvent = getFinalEvent(ev, events);
                    finalEvent.setConfirmedRequests(ev.getCount());
                    UserShortDto initiator = initiatorsByEventId.get(ev.getEventId());
                    double rating = getAnalyzerRating(ev.getEventId());
                    return eventMapper.toEventShortDto(finalEvent, rating, initiator);
                })
                .toList());

        if (params.getSort() != null) {
            if (params.getSort() == EVENT_DATE) {
                eventShortDtos.sort(Comparator.comparing(EventShortDto::getEventDate).reversed());
            } else if (params.getSort() == TOP_RATING) {
                eventShortDtos.sort(Comparator.comparing(EventShortDto::getRating).reversed());
            }
        }

        return eventShortDtos;
    }

    @Override
    public EventFullDto getById(long eventId) {
        Event event = getEvent(eventId);
        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new NotFoundException("Event is not published");
        }

        UserShortDto initiator = userClient.getUserById(event.getInitiatorId());
        Integer requests = requestClient.getCountConfirmedRequest(event.getId());

        double rating = getAnalyzerRating(eventId);
        event.setConfirmedRequests(requests);
        return eventMapper.toEventFullDto(event, rating, initiator);
    }

    @Override
    public List<EventFullDto> getAll(AdminEventRequestParams params) {
        QEvent event = QEvent.event;
        List<BooleanExpression> conditions = new ArrayList<>();

        int from = params.getFrom();
        int size = params.getSize();

        PageRequest pageRequest = PageRequest.of(from > 0 ? from / size : 0, size);

        conditions.add(event.eventDate.after(params.getRangeStart()));
        conditions.add(event.eventDate.before(params.getRangeEnd()));
        if (params.getUsers() != null && !params.getUsers().isEmpty()) {
            conditions.add(event.initiatorId.in(params.getUsers()));
        }
        if (params.getStates() != null && !params.getStates().isEmpty()) {
            conditions.add(event.state.in(params.getStates()));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            conditions.add(event.category.id.in(params.getCategories()));
        }
        BooleanExpression finalConditional = conditions.stream().reduce(BooleanExpression::and).get();


        List<Event> events = eventRepository.findAll(finalConditional, pageRequest).getContent();
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, UserShortDto> initiatorsByEventId = getInitiators(events);

        List<EventCountByRequest> eventsIdWithConfirmedRequest
                = requestClient.getConfirmedRequest(eventIds);

        return eventsIdWithConfirmedRequest.stream()
                .map(ev -> {
                    Event finalEvent = getFinalEvent(ev, events);
                    finalEvent.setConfirmedRequests(ev.getCount());
                    UserShortDto initiator = initiatorsByEventId.get(ev.getEventId());
                    double rating = getAnalyzerRating(ev.getEventId());
                    return eventMapper.toEventFullDto(finalEvent, rating, initiator);
                })
                .toList();
    }

    @Override
    public List<EventShortDto> getAll(PrivateEventParams params) {
        QEvent event = QEvent.event;
        List<BooleanExpression> conditions = new ArrayList<>();
        conditions.add(event.initiatorId.eq(params.getUserId()));
        BooleanExpression finalCondition = conditions.stream()
                .reduce(BooleanExpression::and)
                .orElse(null);

        PageRequest pageRequest = PageRequest.of(params.getFrom() / params.getSize(), params.getSize());

        List<Event> events = eventRepository.findAll(finalCondition, pageRequest).getContent();

        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Map<Long, UserShortDto> initiatorsByEventId = getInitiators(events);

        List<EventCountByRequest> eventsIdWithConfirmedRequest
                = requestClient.getConfirmedRequest(eventIds);

        return eventsIdWithConfirmedRequest.stream()
                .map(ev -> {
                    Event finalEvent = getFinalEvent(ev, events);
                    finalEvent.setConfirmedRequests(ev.getCount());
                    UserShortDto initiator = initiatorsByEventId.get(ev.getEventId());
                    double rating = getAnalyzerRating(ev.getEventId());
                    return eventMapper.toEventShortDto(finalEvent, rating, initiator);
                })
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto create(long userId, NewEventDto newEventDto) {
        UserShortDto initiator = getUser(userId);
        if (newEventDto.getEventDate().minusHours(2).isBefore(LocalDateTime.now())) {
            throw new ConflictException("Different with now less than 2 hours");
        }
        Category category = getCategory(newEventDto.getCategory());
        Location location = locationMapper.toLocation(newEventDto.getLocation());
        locationRepository.save(location);

        Event event = eventMapper.toEvent(newEventDto, category, location, initiator.getId(), EventState.PENDING,
                LocalDateTime.now());
        event.setConfirmedRequests(0);
        Event saved = eventRepository.save(event);
        return eventMapper.toEventFullDto(saved, 0, initiator);
    }

    @Override
    public EventFullDto getById(long userId, long eventId) {
        UserShortDto initiator = getUser(userId);
        Event event = getEvent(eventId);
        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("The user is not the initiator of the event");
        }
        double rating = getAnalyzerRating(event.getId());
        return eventMapper.toEventFullDto(event, rating, initiator);
    }

    @Override
    @Transactional
    public EventFullDto update(long userId, long eventId, UpdateEventUserRequest updateEventUserRequest) {
        Event event = getEvent(eventId);
        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("The user is not the initiator of the event");
        }
        if (event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("You can't change an event that has already been published");
        }
        if (event.getEventDate().minusHours(2).isBefore(LocalDateTime.now())) {
            throw new ConflictException("Different with now less than 2 hours");
        }

        if (updateEventUserRequest.getAnnotation() != null) {
            event.setAnnotation(updateEventUserRequest.getAnnotation());
        }
        if (updateEventUserRequest.getCategory() != null) {
            Category category = getCategory(updateEventUserRequest.getCategory());
            event.setCategory(category);
        }
        if (updateEventUserRequest.getDescription() != null) {
            event.setDescription(updateEventUserRequest.getDescription());
        }
        if (updateEventUserRequest.getEventDate() != null) {
            event.setEventDate(updateEventUserRequest.getEventDate());
        }
        if (updateEventUserRequest.getLocation() != null) {
            Location location = locationRepository.save(locationMapper.toLocation(updateEventUserRequest.getLocation()));
            event.setLocation(location);
        }
        if (updateEventUserRequest.getPaid() != null) {
            event.setPaid(updateEventUserRequest.getPaid());
        }
        if (updateEventUserRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateEventUserRequest.getParticipantLimit());
        }
        if (updateEventUserRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateEventUserRequest.getRequestModeration());
        }
        if (updateEventUserRequest.getTitle() != null) {
            event.setTitle(updateEventUserRequest.getTitle());
        }
        if (updateEventUserRequest.getStateAction() != null) {
            if (updateEventUserRequest.getStateAction().equals(EventAction.SEND_TO_REVIEW)) {
                event.setState(EventState.PENDING);
            } else if (updateEventUserRequest.getStateAction().equals(EventAction.CANCEL_REVIEW)) {
                event.setState(EventState.CANCELED);
            }
        }
        Event saved = eventRepository.save(event);
        UserShortDto initiator = getUser(event.getInitiatorId());
        double rating = getAnalyzerRating(saved.getId());
        return eventMapper.toEventFullDto(saved, rating, initiator);
    }

    @Override
    @Transactional
    public EventFullDto update(long eventId, UpdateEventAdminRequest eventDto) {
        Event savedEvent = getEvent(eventId);
        if (eventDto.getStateAction() != null) {
            if (eventDto.getStateAction().equals(EventAction.PUBLISH_EVENT) && !savedEvent.getState().equals(EventState.PENDING)) {
                throw new ConflictException("Event in state " + savedEvent.getState() + " can not be published");
            }
            if (eventDto.getStateAction().equals(EventAction.REJECT_EVENT) && savedEvent.getState().equals(EventState.PUBLISHED)) {
                throw new ConflictException("Event in state " + savedEvent.getState() + " can not be rejected");
            }
            if (eventDto.getStateAction().equals(EventAction.REJECT_EVENT)) {
                savedEvent.setState(EventState.CANCELED);
            }
        }

        if (eventDto.getEventDate() != null) {
            if (savedEvent.getState().equals(EventState.PUBLISHED) && savedEvent.getPublishedOn().plusHours(1).isAfter(eventDto.getEventDate())) {
                throw new ConflictException("Different with publishedOn less than 1 hours");
            }
            savedEvent.setEventDate(eventDto.getEventDate());
        }
        if (eventDto.getAnnotation() != null) {
            savedEvent.setAnnotation(eventDto.getAnnotation());
        }
        if (eventDto.getDescription() != null) {
            savedEvent.setDescription(eventDto.getDescription());
        }
        if (eventDto.getLocation() != null) {
            locationRepository.save(locationMapper.toLocation(eventDto.getLocation()));
        }
        if (eventDto.getCategory() != null) {
            Category category = getCategory(eventDto.getCategory());
            savedEvent.setCategory(category);
        }
        if (eventDto.getPaid() != null) {
            savedEvent.setPaid(eventDto.getPaid());
        }
        if (eventDto.getParticipantLimit() != null) {
            savedEvent.setParticipantLimit(eventDto.getParticipantLimit());
        }
        if (eventDto.getRequestModeration() != null) {
            savedEvent.setRequestModeration(eventDto.getRequestModeration());
        }
        if (eventDto.getTitle() != null) {
            savedEvent.setTitle(eventDto.getTitle());
        }
        if (eventDto.getStateAction() != null && eventDto.getStateAction().equals(EventAction.PUBLISH_EVENT)) {
            savedEvent.setState(EventState.PUBLISHED);
        }
        savedEvent.setPublishedOn(LocalDateTime.now());
        Integer requests = requestClient.getCountConfirmedRequest(eventId);
        savedEvent.setConfirmedRequests(requests);

        Event updated = eventRepository.save(savedEvent);

        UserShortDto initiator = getUser(updated.getInitiatorId());

        double rating = getAnalyzerRating(savedEvent.getId());
        return eventMapper.toEventFullDto(updated, rating, initiator);
    }

    @Override
    public EventRequestDto getByIdForRequest(long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found with id" + eventId));
        return eventMapper.toEventRequestDto(event);
    }

    @Override
    public EventRequestDto getByIdAndInitiatorId(long eventId, long userId) {
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found with id" + eventId + "for user" + userId));
        return eventMapper.toEventRequestDto(event);
    }

    private Map<Long, UserShortDto> getInitiators(List<Event> events) {
        List<Long> initiatorsId = new ArrayList<>();
        for (Event event : events) {
            initiatorsId.add(event.getInitiatorId());
        }
        List<UserShortDto> allUsersByIds = userClient.getUsersByIds(initiatorsId);
        Map<Long, UserShortDto> dtoMap = allUsersByIds.stream().collect(Collectors.toMap(UserShortDto::getId, dto -> dto));
        return events.stream()
                .collect(Collectors.toMap(Event::getId,
                        event -> dtoMap.get(event.getInitiatorId())));
    }

    private Event getEvent(long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));
    }

    private UserShortDto getUser(long userId) {
        return userClient.getUserById(userId);
    }

    private Category getCategory(long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id= " + categoryId + " was not found"));
    }

    private static Event getFinalEvent(EventCountByRequest ev, List<Event> events) {
        return events.stream()
                .filter(e -> e.getId().equals(ev.getEventId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Event not found: " + ev.getEventId()));
    }

    private double getAnalyzerRating(long eventId) {
        var stream = analyzerClient.getInteractionsCount(List.of(eventId));

        return stream.findFirst()
                .map(RecommendationsMessages.RecommendedEventProto::getScore)
                .orElse(0.0);
    }
}
