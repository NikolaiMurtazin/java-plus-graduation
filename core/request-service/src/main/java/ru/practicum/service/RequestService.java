package ru.practicum.service;

import ru.practicum.request.dto.EventCountByRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.dto.RequestParamsUpdate;

import java.util.List;

public interface RequestService {

    List<ParticipationRequestDto> getAll(long userId);

    ParticipationRequestDto create(long userId, long eventId);

    ParticipationRequestDto cancel(long userId, long requestId);

    List<ParticipationRequestDto> findRequestsOnUserEvent(long userId, long eventId);

    EventRequestStatusUpdateResult updateStatus(RequestParamsUpdate params);

    Integer getCountConfirmedRequest(long eventId);

    List<EventCountByRequest> getConfirmedRequest(List<Long> eventIds);

    boolean userAttendedEvent(long userId, long eventId);
}