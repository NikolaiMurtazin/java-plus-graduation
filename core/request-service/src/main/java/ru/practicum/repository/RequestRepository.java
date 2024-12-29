package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.model.Request;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    List<Request> findByEventIdAndRequesterId(long eventId, long userId);

    List<Request> findByRequesterId(long userId);

    @Query(value = "SELECT (COUNT(r.id)>=?2) " +
            "FROM Request r " +
            "WHERE r.eventId = ?1 AND r.status= 'CONFIRMED'")
    boolean isParticipantLimitReached(long eventId, int limit);

    @Query(value = "SELECT COUNT(r.id) AS count " +
            "FROM Request r " +
            "WHERE r.eventId = ?1 AND r.status = 'CONFIRMED'")
    Integer countConfirmedRequest(long eventId);

    List<Request> findByEventId(long eventId);
}