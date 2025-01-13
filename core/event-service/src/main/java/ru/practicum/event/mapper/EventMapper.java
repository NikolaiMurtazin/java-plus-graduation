package ru.practicum.event.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.practicum.category.model.Category;
import ru.practicum.event.dto.event.EventFullDto;
import ru.practicum.event.dto.event.EventRequestDto;
import ru.practicum.event.dto.event.EventShortDto;
import ru.practicum.event.dto.event.NewEventDto;
import ru.practicum.event.model.Event;
import ru.practicum.event.enums.EventState;
import ru.practicum.location.model.Location;
import ru.practicum.user.dto.UserShortDto;

import java.time.LocalDateTime;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface EventMapper {
    @Mapping(target = "location", source = "event.location")
    @Mapping(target = "id", source = "event.id")
    EventFullDto toEventFullDto(final Event event, final double rating, final UserShortDto initiator);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publishedOn", ignore = true)
    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "category", source = "category")
    @Mapping(target = "location", source = "location")
    @Mapping(target = "initiatorId", source = "initiatorId")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "createdOn", source = "createdOn")
    Event toEvent(final NewEventDto newEventDto, final Category category, final Location location,
                  final long initiatorId, final EventState state, LocalDateTime createdOn);


    @Mapping(target = "id", source = "event.id")
    EventShortDto toEventShortDto(final Event event, final double rating, final UserShortDto initiator);

    EventRequestDto toEventRequestDto(final Event event);
}
