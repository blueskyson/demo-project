package com.example.esgaward.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.esgaward.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findAllByOrderByUsernameAsc();
}
