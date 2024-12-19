package ru.practicum.event.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatsParams;
import ru.practicum.ViewStatsDTO;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.config.StatServiceAdapter;
import ru.practicum.event.dto.AdminEventRequestParams;
import ru.practicum.event.dto.EventAction;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventRatingDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.dto.NewEventDto;
import ru.practicum.event.dto.PrivateEventParams;
import ru.practicum.event.dto.PublicEventRequestParams;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.dto.UpdateEventUserRequest;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.model.QEvent;
import ru.practicum.event.model.Sort;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exeption.ConflictException;
import ru.practicum.exeption.NotFoundException;
import ru.practicum.location.model.Location;
import ru.practicum.location.repository.LocationRepository;
import ru.practicum.rating.repository.RatingRepository;
import ru.practicum.request.dto.EventCountByRequest;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;
    private final RequestRepository requestRepository;
    private final StatServiceAdapter statServiceAdapter;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final RatingRepository ratingRepository;

    // Константа для универсального метода получения статистики
    private static final LocalDateTime DEFAULT_START_DATE = LocalDateTime.MIN;

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
            conditions.add(event.description.containsIgnoreCase(params.getText())
                    .or(event.annotation.containsIgnoreCase(params.getText())));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            conditions.add(event.category.id.in(params.getCategories()));
        }
        if (params.getPaid() != null) {
            conditions.add(event.paid.eq(params.getPaid()));
        }

        BooleanExpression finalConditional = conditions.stream()
                .reduce(BooleanExpression::and)
                .orElse(null);

        PageRequest pageRequest = PageRequest.of(from > 0 ? from / size : 0, size);
        List<Event> events = eventRepository.findAll(finalConditional, pageRequest).getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventCountByRequest> eventsIdWithConfirmedRequest = params.getOnlyAvailable()
                ? requestRepository.findConfirmedRequestWithLimitCheck(events)
                : requestRepository.findConfirmedRequestWithoutLimitCheck(events);

        // Получение списка URI для статистики
        List<String> uris = eventsIdWithConfirmedRequest.stream()
                .map(ev -> "/events/" + ev.getEventId())
                .collect(Collectors.toList());

        List<ViewStatsDTO> viewStatsDTOS = fetchViewStats(uris);
        List<EventRatingDto> eventRatingDtos = getEventRatingDtos(events);

        List<EventShortDto> eventShortDtos = eventsIdWithConfirmedRequest.stream()
                .map(ev -> {
                    Event finalEvent = getFinalEvent(ev, events);
                    long rating = getRating(ev, eventRatingDtos);
                    long views = getViews(ev.getEventId(), viewStatsDTOS);
                    finalEvent.setConfirmedRequests(Math.toIntExact(ev.getCount()));
                    return eventMapper.toEventShortDto(finalEvent, rating, views);
                })
                .collect(Collectors.toList());

        if (params.getSort() != null) {
            eventShortDtos.sort(getComparator(params.getSort()));
        }

        return eventShortDtos;
    }

    @Override
    public EventFullDto getById(long eventId) {
        Event event = getEvent(eventId);
        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new NotFoundException("Event is not published");
        }

        Integer requests = requestRepository.countConfirmedRequest(eventId);
        long rating = getEventRating(event);
        long views = getEventViews(event);
        event.setConfirmedRequests(requests);
        return eventMapper.toEventFullDto(event, rating, views);
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
            conditions.add(event.initiator.id.in(params.getUsers()));
        }
        if (params.getStates() != null && !params.getStates().isEmpty()) {
            conditions.add(event.state.in(params.getStates()));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            conditions.add(event.category.id.in(params.getCategories()));
        }
        BooleanExpression finalConditional = conditions.stream()
                .reduce(BooleanExpression::and)
                .orElse(null);

        List<Event> events = eventRepository.findAll(finalConditional, pageRequest).getContent();

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventCountByRequest> eventsIdWithConfirmedRequest = requestRepository.findConfirmedRequestWithoutLimitCheck(events);
        List<EventRatingDto> eventRatingDtos = getEventRatingDtos(events);

        // Получение списка URI для статистики
        List<String> uris = eventsIdWithConfirmedRequest.stream()
                .map(ev -> "/events/" + ev.getEventId())
                .collect(Collectors.toList());

        List<ViewStatsDTO> viewStatsDTOS = fetchViewStats(uris);

        return eventsIdWithConfirmedRequest.stream()
                .map(ev -> {
                    Event finalEvent = getFinalEvent(ev, events);
                    long rating = getRating(ev, eventRatingDtos);
                    long views = getViews(ev.getEventId(), viewStatsDTOS);
                    finalEvent.setConfirmedRequests(Math.toIntExact(ev.getCount()));
                    return eventMapper.toEventFullDto(finalEvent, rating, views);
                })
                .collect(Collectors.toList());
    }

    // Приватные методы для работы с пользователями

    @Override
    public List<EventShortDto> getAll(PrivateEventParams params) {
        QEvent event = QEvent.event;
        List<BooleanExpression> conditions = new ArrayList<>();
        conditions.add(event.initiator.id.eq(params.getUserId()));
        BooleanExpression finalCondition = conditions.stream()
                .reduce(BooleanExpression::and)
                .orElse(null);

        PageRequest pageRequest = PageRequest.of(params.getFrom() / params.getSize(), params.getSize());

        List<Event> events = eventRepository.findAll(finalCondition, pageRequest).getContent();

        List<EventCountByRequest> eventsIdWithConfirmedRequest = requestRepository.findConfirmedRequestWithoutLimitCheck(events);

        // Получение списка URI для статистики
        List<String> uris = eventsIdWithConfirmedRequest.stream()
                .map(ev -> "/events/" + ev.getEventId())
                .collect(Collectors.toList());

        List<ViewStatsDTO> viewStatsDTOS = fetchViewStats(uris);
        List<EventRatingDto> eventRatingDtos = getEventRatingDtos(events);

        return eventsIdWithConfirmedRequest.stream()
                .map(ev -> {
                    Event finalEvent = getFinalEvent(ev, events);
                    long views = getViews(ev.getEventId(), viewStatsDTOS);
                    long rating = getRating(ev, eventRatingDtos);
                    finalEvent.setConfirmedRequests(Math.toIntExact(ev.getCount()));
                    return eventMapper.toEventShortDto(finalEvent, rating, views);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto create(long userId, NewEventDto newEventDto) {
        User initiator = getUser(userId);
        if (newEventDto.getEventDate().minusHours(2).isBefore(LocalDateTime.now())) {
            throw new ConflictException("Event date is less than 2 hours from now");
        }
        Category category = getCategory(newEventDto.getCategory());
        Location location = locationRepository.save(newEventDto.getLocation());

        Event event = eventMapper.toEvent(newEventDto, category, location, initiator, EventState.PENDING,
                LocalDateTime.now());
        event.setConfirmedRequests(0);
        Event saved = eventRepository.save(event);
        return eventMapper.toEventFullDto(saved, 0, 0L);
    }

    @Override
    public EventFullDto getById(long userId, long eventId) {
        getUser(userId);
        Event event = getEvent(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("The user is not the initiator of the event");
        }
        long rating = getEventRating(event);
        long eventViews = getEventViews(event);
        return eventMapper.toEventFullDto(event, rating, eventViews);
    }

    @Override
    @Transactional
    public EventFullDto update(long userId, long eventId, UpdateEventUserRequest updateEventUserRequest) {
        Event event = getEvent(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("The user is not the initiator of the event");
        }
        if (event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("You can't change an event that has already been published");
        }
        if (updateEventUserRequest.getEventDate() != null &&
                updateEventUserRequest.getEventDate().minusHours(2).isBefore(LocalDateTime.now())) {
            throw new ConflictException("Event date is less than 2 hours from now");
        }

        // Обновление полей события
        updateEventFields(event, updateEventUserRequest);

        Event saved = eventRepository.save(event);
        long rating = getEventRating(event);
        long eventViews = getEventViews(saved);
        return eventMapper.toEventFullDto(saved, rating, eventViews);
    }

    @Override
    @Transactional
    public EventFullDto update(long eventId, UpdateEventAdminRequest eventDto) {
        Event savedEvent = getEvent(eventId);
        if (eventDto.getStateAction() != null) {
            handleStateAction(savedEvent, eventDto.getStateAction());
        }

        if (eventDto.getEventDate() != null) {
            if (savedEvent.getState().equals(EventState.PUBLISHED) &&
                    savedEvent.getPublishedOn().plusHours(1).isAfter(eventDto.getEventDate())) {
                throw new ConflictException("Event date is less than 1 hour after publication");
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
            savedEvent.setLocation(locationRepository.save(eventDto.getLocation()));
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
            savedEvent.setPublishedOn(LocalDateTime.now());
        }
        Integer requests = requestRepository.countConfirmedRequest(eventId);
        savedEvent.setConfirmedRequests(requests);

        Event updated = eventRepository.save(savedEvent);

        long rating = getEventRating(updated);
        long eventViews = getEventViews(updated);
        return eventMapper.toEventFullDto(updated, rating, eventViews);
    }

    @Override
    public List<Event> getByIds(List<Long> events) {
        return eventRepository.findAllById(events);
    }

    // Универсальный метод для получения статистики просмотров
    private List<ViewStatsDTO> fetchViewStats(List<String> uris) {
        if (uris.isEmpty()) {
            return Collections.emptyList();
        }
        StatsParams statsParams = StatsParams.builder()
                .uris(uris)
                .unique(true)
                .start(DEFAULT_START_DATE) // Используем минимальную дату для охвата всех возможных записей
                .end(LocalDateTime.now())
                .build();

        return statServiceAdapter.getStats(statsParams);
    }

    // Получение рейтингов для списка событий
    private List<EventRatingDto> getEventRatingDtos(List<Event> events) {
        return ratingRepository.countEventsRating(events);
    }

    // Получение рейтинга для конкретного события
    private long getRating(EventCountByRequest event, List<EventRatingDto> eventRatingDtos) {
        return eventRatingDtos.stream()
                .filter(ev -> ev.getEventId().equals(event.getEventId()))
                .map(EventRatingDto::getRating)
                .findFirst()
                .orElse(0L);
    }

    // Получение рейтинга (разница лайков и дизлайков)
    private long getEventRating(Event event) {
        return ratingRepository.countLikesByEvent(event) - ratingRepository.countDislikesByEvent(event);
    }

    // Получение просмотров для конкретного события
    private long getEventViews(Event event) {
        List<String> uris = List.of("/events/" + event.getId());
        List<ViewStatsDTO> stats = fetchViewStats(uris);
        return stats.stream()
                .findFirst()
                .map(ViewStatsDTO::getHits)
                .orElse(0L);
    }

    // Получение просмотров по ID события из списка статистики
    private long getViews(long eventId, List<ViewStatsDTO> viewStatsDTOS) {
        return viewStatsDTOS.stream()
                .filter(stat -> stat.getUri().equals("/events/" + eventId))
                .map(ViewStatsDTO::getHits)
                .findFirst()
                .orElse(0L);
    }

    // Получение окончательного объекта события по ID
    private Event getFinalEvent(EventCountByRequest ev, List<Event> events) {
        return events.stream()
                .filter(e -> e.getId().equals(ev.getEventId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Event not found: " + ev.getEventId()));
    }

    // Метод для получения списка URI и вызова универсального метода статистики
    private List<ViewStatsDTO> getViewStatsDTOS(List<EventCountByRequest> eventsIdWithConfirmedRequest) {
        List<String> uris = eventsIdWithConfirmedRequest.stream()
                .map(ev -> "/events/" + ev.getEventId())
                .collect(Collectors.toList());

        return fetchViewStats(uris);
    }

    // Метод для получения пользователя по ID
    private User getUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id " + userId));
    }

    // Метод для получения категории по ID
    private Category getCategory(long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category with id " + categoryId + " was not found"));
    }

    // Метод для получения события по ID
    private Event getEvent(long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found with id: " + eventId));
    }

    // Метод для обновления полей события
    private void updateEventFields(Event event, UpdateEventUserRequest updateEventUserRequest) {
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
            Location location = locationRepository.save(updateEventUserRequest.getLocation());
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
            handleUserStateAction(event, updateEventUserRequest.getStateAction());
        }
    }

    // Метод для обработки действий пользователя с состоянием события
    private void handleUserStateAction(Event event, EventAction stateAction) {
        if (stateAction.equals(EventAction.SEND_TO_REVIEW)) {
            event.setState(EventState.PENDING);
        } else if (stateAction.equals(EventAction.CANCEL_REVIEW)) {
            event.setState(EventState.CANCELED);
        }
    }

    // Метод для обработки действий администратора с состоянием события
    private void handleStateAction(Event event, EventAction stateAction) {
        if (stateAction.equals(EventAction.PUBLISH_EVENT) && !event.getState().equals(EventState.PENDING)) {
            throw new ConflictException("Event in state " + event.getState() + " cannot be published");
        }
        if (stateAction.equals(EventAction.REJECT_EVENT) && event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Event in state " + event.getState() + " cannot be rejected");
        }
        if (stateAction.equals(EventAction.REJECT_EVENT)) {
            event.setState(EventState.CANCELED);
        }
    }

    // Универсальный компаратор для сортировки
    private Comparator<EventShortDto> getComparator(Sort sort) {
        if (sort == null) {
            return Comparator.comparing(EventShortDto::getId); // Дефолтная сортировка по ID
        }
        return switch (sort) {
            case EVENT_DATE -> Comparator.comparing(EventShortDto::getEventDate).reversed();
            case VIEWS -> Comparator.comparing(EventShortDto::getViews).reversed();
            case TOP_RATING -> Comparator.comparing(EventShortDto::getRating).reversed();
            default -> Comparator.comparing(EventShortDto::getId);
        };
    }
}
