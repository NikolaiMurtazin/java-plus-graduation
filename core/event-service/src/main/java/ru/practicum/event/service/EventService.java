package ru.practicum.event.service;

import ru.practicum.event.dto.event.AdminEventRequestParams;
import ru.practicum.event.dto.event.EventFullDto;
import ru.practicum.event.dto.event.EventRequestDto;
import ru.practicum.event.dto.event.EventShortDto;
import ru.practicum.event.dto.event.NewEventDto;
import ru.practicum.event.dto.event.PrivateEventParams;
import ru.practicum.event.dto.event.PublicEventRequestParams;
import ru.practicum.event.dto.event.UpdateEventAdminRequest;
import ru.practicum.event.dto.event.UpdateEventUserRequest;

import java.util.List;

public interface EventService {

    List<EventShortDto> getAll(PrivateEventParams params);

    EventFullDto create(long userId, NewEventDto newEventDto);

    EventFullDto getById(long eventId);

    EventFullDto update(long userId, long eventId, UpdateEventUserRequest updateEventUserRequest);

    List<EventShortDto> getAll(PublicEventRequestParams params);

    List<EventFullDto> getAll(AdminEventRequestParams params);

    EventFullDto getById(long userId, long eventId);

    EventFullDto update(long eventId, UpdateEventAdminRequest event);

    EventRequestDto getByIdForRequest(long eventId);

    EventRequestDto getByIdAndInitiatorId(long eventId, long userId);
}
