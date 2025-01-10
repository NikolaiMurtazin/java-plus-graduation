package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.stats.proto.RecommendationsMessages;
import ru.practicum.model.EventSimilarity;
import ru.practicum.model.RecommendedEvent;
import ru.practicum.model.UserAction;
import ru.practicum.repository.EventSimilarityRepository;
import ru.practicum.repository.UserActionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserActionRepository userActionRepo;
    private final EventSimilarityRepository similarityRepo;

    // Найти похожие мероприятия
    public List<RecommendedEvent> getSimilarEvents(RecommendationsMessages.SimilarEventsRequestProto request) {
        long eventId = request.getEventId();
        long userId  = request.getUserId();
        int maxRes   = request.getMaxResults();

        // 1) Найти все EventSimilarityEntity где eventA=eventId или eventB=eventId
        List<EventSimilarity> simList = similarityRepo.findByEventAOrEventB(eventId, eventId);

        // 2) Собрать множество мероприятий, с которыми userId уже взаимодействовал
        Set<Long> interacted = userInteracted(userId);

        // 3) Сформировать список (eventX, score), убрав already interacted
        List<RecommendedEvent> result = new ArrayList<>();
        for (EventSimilarity e : simList) {
            long other = (e.getEventA() == eventId) ? e.getEventB() : e.getEventA();
            // исключить, если userId взаимодействовал
            if (!interacted.contains(other)) {
                result.add(new RecommendedEvent(other, e.getScore()));
            }
        }

        // 4) Сортируем по убыванию score, берём top N
        result.sort(Comparator.comparingDouble(RecommendedEvent::score).reversed());
        if (result.size() > maxRes) {
            return result.subList(0, maxRes);
        }
        return result;
    }

    // Предсказание для пользователя
    public List<RecommendedEvent> getRecommendationsForUser(RecommendationsMessages.UserPredictionsRequestProto request) {
        long userId = request.getUserId();
        int maxRes  = request.getMaxResults();

        // Собираем все действия userId (от новых к старым)
        List<UserAction> all = userActionRepo.findByUserId(userId);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        // Сортируем по lastInteraction desc
        all.sort((a,b) -> b.getLastInteraction().compareTo(a.getLastInteraction()));

        // Возьмём последние 5 (например)
        int N = Math.min(5, all.size());
        List<UserAction> recent = all.subList(0, N);

        // userId уже interacted
        Set<Long> interacted = userInteracted(userId);

        // Собираем все похожие мероприятия на recent, исключая interacted
        Map<Long, Float> bestScoreMap = new HashMap<>();
        for (UserAction r : recent) {
            long ev = r.getEventId();
            // find all similarities
            List<EventSimilarity> simList = similarityRepo.findByEventAOrEventB(ev, ev);
            for (EventSimilarity e : simList) {
                long other = (e.getEventA() == ev) ? e.getEventB() : e.getEventA();
                if (interacted.contains(other)) {
                    // пропускаем
                    continue;
                }
                // берём максимально возможное сходство
                float oldVal = bestScoreMap.getOrDefault(other, 0f);
                if (e.getScore() > oldVal) {
                    bestScoreMap.put(other, e.getScore());
                }
            }
        }

        // сортируем bestScoreMap по score desc
        List<RecommendedEvent> result = bestScoreMap.entrySet().stream()
                .map(e -> new RecommendedEvent(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(RecommendedEvent::score).reversed())
                .limit(maxRes)
                .collect(Collectors.toList());

        return result;
    }

    // Сумма взаимодействий
    public List<RecommendedEvent> getInteractionsCount(RecommendationsMessages.InteractionsCountRequestProto request) {
        List<Long> events = request.getEventIdList();
        List<RecommendedEvent> result = new ArrayList<>();

        for (Long e : events) {
            List<UserAction> list = userActionRepo.findByEventId(e);
            double sum = 0.0;
            for (UserAction uae : list) {
                sum += uae.getMaxWeight();
            }
            result.add(new RecommendedEvent(e, (float) sum));
        }
        return result;
    }

    private Set<Long> userInteracted(long userId) {
        return userActionRepo.findByUserId(userId)
                .stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toSet());
    }
}
