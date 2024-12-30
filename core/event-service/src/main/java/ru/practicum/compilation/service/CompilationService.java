package ru.practicum.compilation.service;

import ru.practicum.event.dto.compilation.CompilationDto;
import ru.practicum.event.dto.compilation.NewCompilationDto;
import ru.practicum.event.dto.compilation.PublicCompilationParams;
import ru.practicum.event.dto.compilation.UpdateCompilationRequest;

import java.util.List;

public interface CompilationService {
    CompilationDto create(NewCompilationDto newCompilationDto);

    void delete(long compId);

    CompilationDto update(long compId, UpdateCompilationRequest updateCompilationRequest);

    List<CompilationDto> getAll(PublicCompilationParams params);

    CompilationDto getById(long compId);
}
