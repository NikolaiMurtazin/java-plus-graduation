package ru.practicum.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Mappings;
import ru.practicum.rating.dto.RatingDto;
import ru.practicum.model.Rating;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR
)
public interface RatingMapper {

    @Mappings({
            @Mapping(source = "userId", target = "userId"),
    })
    RatingDto toRatingDto(final Rating rating);
}
