package com.example.esgaward.awardevent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AwardEventRepository extends JpaRepository<AwardEvent, Long> {

    List<AwardEvent> findAllByOrderByDeadlineDesc();
}
