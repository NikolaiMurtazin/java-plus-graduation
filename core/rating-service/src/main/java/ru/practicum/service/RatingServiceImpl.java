package ru.practicum.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.event.dto.event.EventRatingDto;
import ru.practicum.event.dto.event.EventRequestDto;
import ru.practicum.event.enums.EventState;
import ru.practicum.event.feign.EventClient;
import ru.practicum.exeption.ConflictException;
import ru.practicum.exeption.NotFoundException;
import ru.practicum.rating.dto.RatingDto;
import ru.practicum.mapper.RatingMapper;
import ru.practicum.model.QRating;
import ru.practicum.model.Rating;
import ru.practicum.repository.RatingRepository;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.feign.UserClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private final RatingRepository ratingRepository;
    private final EventClient eventClient;
    private final UserClient userClient;
    private final RatingMapper ratingMapper;

    @Override
    public List<RatingDto> getAllById(long userId, int from, int size) {
        QRating rating = QRating.rating;
        List<BooleanExpression> conditions = new ArrayList<>();
        conditions.add(rating.userId.eq(userId));
        BooleanExpression finalCondition = conditions.stream()
                .reduce(BooleanExpression::and)
                .orElse(null);

        PageRequest pageRequest = PageRequest.of(from / size, size);

        List<Rating> ratings = ratingRepository.findAll(finalCondition, pageRequest).getContent();

        return ratings.stream()
                .map(ratingMapper::toRatingDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addRating(long userId, long eventId, boolean isLike) {
        UserShortDto user = getUser(userId);
        EventRequestDto event = getEvent(eventId);

        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("The initiator of the event can't add a request to participate in his event");
        }

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Cannot react because the event is not published");
        }

        Optional<Rating> existingRating = ratingRepository.findByUserIdAndEventId(user.getId(), event.getId());

        if (existingRating.isPresent()) {
            Rating rating = existingRating.get();
            if (rating.getIsLike() == isLike) {
                throw new ConflictException((isLike ? "Like" : "Dislike") + " already exists");
            } else {
                rating.setIsLike(isLike);
                ratingRepository.save(rating);
            }
        } else {
            Rating rating = Rating.builder()
                    .created(LocalDateTime.now())
                    .userId(user.getId())
                    .eventId(event.getId())
                    .isLike(isLike)
                    .build();
            ratingRepository.save(rating);
        }
    }

    @Transactional
    public void removeRating(long userId, long eventId, boolean isLike) {
        UserShortDto user = getUser(userId);
        EventRequestDto event = getEvent(eventId);

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Cannot remove reaction because the event is not published");
        }

        Rating rating = getRating(user.getId(), event.getId());

        if (rating.getIsLike() == isLike) {
            ratingRepository.delete(rating);
        } else {
            throw new ConflictException("No " + (isLike ? "like" : "dislike") + " found to remove");
        }
    }

    @Override
    public Integer getCountEventRating(long eventId) {
        int likes = Optional.of(ratingRepository.countLikesByEvent(eventId)).orElse(0);
        int dislikes = Optional.of(ratingRepository.countDislikesByEvent(eventId)).orElse(0);
        return likes - dislikes;
    }

    @Override
    public List<EventRatingDto> getEventsRating(List<Long> eventIds) {
        return ratingRepository.countEventsRating(eventIds);
    }

    private UserShortDto getUser(long userId) {
        return userClient.getUserById(userId);
    }

    private EventRequestDto getEvent(long eventId) {
        return eventClient.getEventById(eventId);
    }

    private Rating getRating(long userId, long eventId) {
        return ratingRepository.findByUserIdAndEventId(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Rating not found"));
    }
}
