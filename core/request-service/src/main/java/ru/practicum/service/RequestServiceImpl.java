package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.event.dto.event.EventRequestDto;
import ru.practicum.event.enums.EventState;
import ru.practicum.event.feign.EventClient;
import ru.practicum.exeption.ConflictException;
import ru.practicum.exeption.NotFoundException;
import ru.practicum.request.dto.EventCountByRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.dto.RequestParamsUpdate;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.Request;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.repository.RequestRepository;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.feign.UserClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestServiceImpl implements RequestService {

    private final RequestRepository requestRepository;

    private final RequestMapper requestMapper;

    private final UserClient userClient;

    private final EventClient eventClient;

    @Override
    public List<ParticipationRequestDto> getAll(long userId) {
        UserShortDto user = getUser(userId);
        List<Request> requests = requestRepository.findByRequesterId(user.getId());
        return requests.stream()
                .map(requestMapper::toParticipationRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto create(long userId, long evenId) {
        UserShortDto requester = getUser(userId);
        EventRequestDto event = getEvent(evenId);

        if (event.getInitiatorId() == (userId)) {
            throw new ConflictException("The initiator of the event can't add a request to participate in his event");
        }
        if (!requestRepository.findByEventIdAndRequesterId(evenId, userId).isEmpty()) {
            throw new ConflictException("Repeatable request not allowed");
        }
        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Event not published");
        }
        if (!(event.getParticipantLimit() == 0)) {
            checkEventRequestLimit(event);
        }
        Integer countConfirmedRequest = requestRepository.countConfirmedRequest(evenId);
        event.setConfirmedRequests(countConfirmedRequest);

        Request request = new Request();
        request.setRequesterId(requester.getId());
        request.setEventId(event.getId());
        request.setCreated(LocalDateTime.now());

        if (event.isRequestModeration() && event.getParticipantLimit() != 0) {
            request.setStatus(RequestStatus.PENDING);
        } else {
            request.setStatus(RequestStatus.CONFIRMED);
        }

        return requestMapper.toParticipationRequestDto(requestRepository.save(request));
    }

    @Transactional
    @Override
    public ParticipationRequestDto cancel(long userId, long requestId) {
        getUser(userId);
        Request request = getRequest(requestId);
        if (request.getRequesterId() != userId) {
            throw new ConflictException("User is not requester");
        }
        request.setStatus(RequestStatus.CANCELED);
        Request saved = requestRepository.save(request);
        return requestMapper.toParticipationRequestDto(saved);
    }

    @Override
    public List<ParticipationRequestDto> findRequestsOnUserEvent(long userId, long eventId) {
        UserShortDto user = getUser(userId);
        EventRequestDto event = getUserEvent(eventId, user.getId());

        List<Request> allRequests = requestRepository.findByEventId(event.getId());
        if (allRequests.isEmpty()) {
            return List.of();
        }
        return allRequests.stream().map(requestMapper::toParticipationRequestDto).toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateStatus(RequestParamsUpdate params) {
        UserShortDto user = getUser(params.getUserId());
        EventRequestDto event = getUserEvent(params.getEventId(), user.getId());
        List<Long> requestIds = params.getDto().getRequestIds();
        List<ParticipationRequestDto> updatedRequests = new ArrayList<>();
        if (params.getDto().getStatus().equals(RequestStatus.REJECTED)) {
            for (Long requestId : requestIds) {
                Request request = getRequest(requestId);
                if (request.getStatus().equals(RequestStatus.PENDING)) {
                    request.setStatus(RequestStatus.REJECTED);
                } else {
                    throw new ConflictException("The request have status " + request.getStatus() + " not been rejected yet");
                }
                ParticipationRequestDto participationRequestDto = requestMapper.toParticipationRequestDto(requestRepository.save(request));
                updatedRequests.add(participationRequestDto);
            }
            return new EventRequestStatusUpdateResult(Collections.emptyList(), updatedRequests);
        } else {
            for (Long requestId : requestIds) {
                Request request = getRequest(requestId);
                checkEventRequestLimit(event);
                request.setStatus(RequestStatus.CONFIRMED);
                updatedRequests.add(requestMapper.toParticipationRequestDto(requestRepository.save(request)));
            }
            return new EventRequestStatusUpdateResult(updatedRequests, Collections.emptyList());
        }
    }

    @Override
    public Integer getCountConfirmedRequest(long eventId) {
        return requestRepository.countConfirmedRequest(eventId);
    }

    @Override
    public List<EventCountByRequest> getConfirmedRequest(List<Long> eventIds) {
        return eventIds.stream().map(id -> new EventCountByRequest(id, requestRepository.countConfirmedRequest(id))).toList();
    }

    private void checkEventRequestLimit(EventRequestDto event) {
        if (requestRepository.isParticipantLimitReached(event.getId(), event.getParticipantLimit())) {
            throw new ConflictException("Request limit reached");
        }
    }

    private Request getRequest(long requestId) {
        return requestRepository.findById(requestId).orElseThrow(() -> new NotFoundException("Request not found with id" + requestId));
    }

    private UserShortDto getUser(long userId) {
        return userClient.getUserById(userId);
    }

    private EventRequestDto getEvent(long eventId) {
        return eventClient.getEventById(eventId);
    }

    private EventRequestDto getUserEvent(long eventId, long userId) {
        return eventClient.getByEventIdAndUserId(eventId, userId);
    }
}