package ru.practicum.user.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.user.dto.UserShortDto;

import java.util.List;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {
    @GetMapping("/{userId}")
    UserShortDto getUserById(@PathVariable("userId") long userId);

    @GetMapping
    List<UserShortDto> getUsersByIds(@RequestParam("userIds") List<Long> userIds);
}
