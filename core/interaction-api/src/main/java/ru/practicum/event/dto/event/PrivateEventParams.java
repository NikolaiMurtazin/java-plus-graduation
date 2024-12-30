package ru.practicum.event.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PrivateEventParams {
    private long userId;
    private int from;
    private int size;
}
