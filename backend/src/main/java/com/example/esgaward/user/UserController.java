package com.example.esgaward.user;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public UserController(CurrentUserService currentUserService, UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.from(currentUserService.currentUser());
    }

    /** Users who have logged in at least once; used to pick proposal members. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAllByOrderByUsernameAsc().stream().map(UserDto::from).toList();
    }
}
