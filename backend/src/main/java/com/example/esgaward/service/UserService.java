package com.example.esgaward.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.esgaward.dto.UserDto;
import com.example.esgaward.repository.UserRepository;
import com.example.esgaward.security.CurrentUserService;
import com.example.esgaward.security.Permission;
import com.example.esgaward.security.RequirePermission;

@Service
public class UserService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public UserService(CurrentUserService currentUserService, UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    @RequirePermission(Permission.USER_READ)
    public UserDto me() {
        return UserDto.from(currentUserService.currentUser());
    }

    /** Users who have logged in at least once; used to pick proposal members. */
    @Transactional(readOnly = true)
    @RequirePermission(Permission.USER_READ)
    public List<UserDto> list() {
        return userRepository.findAllByOrderByUsernameAsc().stream().map(UserDto::from).toList();
    }
}
