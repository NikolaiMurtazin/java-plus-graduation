package ru.practicum.location.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.practicum.event.dto.location.LocationDto;
import ru.practicum.location.model.Location;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface LocationMapper {
    Location toLocation(final LocationDto locationDto);
}
