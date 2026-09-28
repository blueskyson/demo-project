package com.example.esgaward.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.esgaward.entity.AwardEvent;

public interface AwardEventRepository extends JpaRepository<AwardEvent, Long> {

    List<AwardEvent> findAllByOrderByDeadlineDesc();
}
