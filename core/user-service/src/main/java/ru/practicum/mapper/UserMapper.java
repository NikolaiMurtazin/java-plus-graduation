package ru.practicum.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.practicum.user.dto.NewUserRequest;
import ru.practicum.user.dto.UserDto;
import ru.practicum.model.User;
import ru.practicum.user.dto.UserShortDto;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    User toUser(final NewUserRequest newUserRequest);

    UserDto toUserDto(final User user);

    UserShortDto toUserShortDto(final User user);
}
