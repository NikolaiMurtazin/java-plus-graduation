package ru.practicum.service;


import ru.practicum.EndpointHitDTO;
import ru.practicum.StatsParams;
import ru.practicum.ViewStatsDTO;

import java.util.List;

public interface StatService {
    EndpointHitDTO save(EndpointHitDTO dto);

    List<ViewStatsDTO> getStats(StatsParams params);
}
