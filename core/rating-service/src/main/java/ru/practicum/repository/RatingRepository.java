package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import ru.practicum.event.dto.event.EventRatingDto;
import ru.practicum.model.Rating;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long>, QuerydslPredicateExecutor<Rating> {
    Optional<Rating> findByUserIdAndEventId(long user, long event);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.eventId = :event AND r.isLike = true")
    int countLikesByEvent(@Param("event") long eventId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.eventId = :event AND r.isLike = false")
    int countDislikesByEvent(@Param("event") long eventId);

    @Query(value = "SELECT new ru.practicum.event.dto.event.EventRatingDto(r.eventId, " +
            "SUM(CASE WHEN r.isLike = true THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN r.isLike = false THEN 1 ELSE 0 END)) " +
            "FROM Rating r " +
            "WHERE r.eventId IN :eventIds " +
            "GROUP BY r.eventId")
    List<EventRatingDto> countEventsRating(List<Long> eventIds);
}
