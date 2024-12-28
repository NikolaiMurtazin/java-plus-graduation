package ru.practicum.event.dto.compilation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublicCompilationParams {
    private Boolean pinned;
    private int from;
    private int size;
}
