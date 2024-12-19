package ru.practicum.event.service;

import ru.practicum.event.dto.AdminEventRequestParams;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.dto.NewEventDto;
import ru.practicum.event.dto.PrivateEventParams;
import ru.practicum.event.dto.PublicEventRequestParams;
import ru.practicum.event.dto.UpdateEventAdminRequest;
import ru.practicum.event.dto.UpdateEventUserRequest;
import ru.practicum.event.model.Event;

import java.util.Collection;
import java.util.List;

public interface EventService {

    //    Приватные пользователи
    List<EventShortDto> getAll(PrivateEventParams params);

    EventFullDto create(long userId, NewEventDto newEventDto);

    EventFullDto getById(long eventId);

    EventFullDto update(long userId, long eventId, UpdateEventUserRequest updateEventUserRequest);

    List<EventShortDto> getAll(PublicEventRequestParams params);

    List<EventFullDto> getAll(AdminEventRequestParams params);

    EventFullDto getById(long userId, long eventId);

    EventFullDto update(long eventId, UpdateEventAdminRequest event);

    Collection<Event> getByIds(List<Long> events);
}
