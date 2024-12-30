package ru.practicum.compilation.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.practicum.event.dto.compilation.CompilationDto;
import ru.practicum.event.dto.compilation.NewCompilationDto;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.event.dto.event.EventShortDto;
import ru.practicum.event.model.Event;

import java.util.Collection;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CompilationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "events", source = "events")
    Compilation toCompilation(final NewCompilationDto newCompilationDto, final Collection<Event> events);

    @Mapping(target = "events", source = "list")
    CompilationDto toCompilationDto(final Compilation compilation, Collection<EventShortDto> list);
}
