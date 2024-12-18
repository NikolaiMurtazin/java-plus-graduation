package ru.practicum.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.EndpointHitDTO;
import ru.practicum.StatClient;
import ru.practicum.StatsParams;
import ru.practicum.ViewStatsDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatServiceAdapter {

    private final StatClient statClient;

    public void saveStats(HttpServletRequest request) {
        try {
            String ip = request.getRemoteAddr();
            String uri = request.getRequestURI();
            EndpointHitDTO dto = EndpointHitDTO.builder()
                    .app("ewm-main-service")
                    .ip(ip)
                    .uri(uri)
                    .timestamp(LocalDateTime.now())
                    .build();

            statClient.saveStats(dto);

        } catch (Exception e) {
            log.warn("Failed to save stats: {}", e.getMessage());
        }
    }

    public List<ViewStatsDTO> getStats(StatsParams params) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String start = params.getStart().format(formatter);
            String end = params.getEnd().format(formatter);

            return statClient.getStats(start, end, params.getUris(), params.getUnique());
        } catch (Exception e) {
            log.warn("Failed to get stats: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
