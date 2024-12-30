package ru.practicum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.practicum.service.UserService;
import ru.practicum.user.dto.UserShortDto;
import ru.practicum.user.feign.UserClient;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
public class InternalUserController implements UserClient {

    private final UserService userService;

    @GetMapping("/{userId}")
    public UserShortDto getUserById(@PathVariable long userId) {
        return userService.getById(userId);
    }

    @Override
    @GetMapping
    public List<UserShortDto> getUsersByIds(@RequestBody List<Long> ids) {
        return userService.getAllUsersByIds(ids);
    }
}
