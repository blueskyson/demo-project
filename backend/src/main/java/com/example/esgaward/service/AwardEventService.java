package com.example.esgaward.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.esgaward.dto.AwardEventDto;
import com.example.esgaward.dto.AwardEventRequest;
import com.example.esgaward.entity.AwardEvent;
import com.example.esgaward.exception.ConflictException;
import com.example.esgaward.exception.NotFoundException;
import com.example.esgaward.repository.AwardEventRepository;
import com.example.esgaward.repository.ProposalRepository;

@Service
public class AwardEventService {

    private final AwardEventRepository awardEventRepository;
    private final ProposalRepository proposalRepository;
    private final Clock clock;

    public AwardEventService(AwardEventRepository awardEventRepository, ProposalRepository proposalRepository,
            Clock clock) {
        this.awardEventRepository = awardEventRepository;
        this.proposalRepository = proposalRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AwardEventDto> list() {
        Instant now = clock.instant();
        return awardEventRepository.findAllByOrderByDeadlineDesc().stream()
                .map(event -> AwardEventDto.from(event, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public AwardEventDto get(Long id) {
        return AwardEventDto.from(find(id), clock.instant());
    }

    @Transactional
    public AwardEventDto create(AwardEventRequest request) {
        Instant now = clock.instant();
        AwardEvent event = awardEventRepository.save(
                new AwardEvent(request.name(), request.description(), request.deadline(), now));
        return AwardEventDto.from(event, now);
    }

    @Transactional
    public AwardEventDto update(Long id, AwardEventRequest request) {
        Instant now = clock.instant();
        AwardEvent event = find(id);
        event.update(request.name(), request.description(), request.deadline(), now);
        return AwardEventDto.from(event, now);
    }

    @Transactional
    public void delete(Long id) {
        AwardEvent event = find(id);
        if (proposalRepository.existsByAwardEventId(id)) {
            throw new ConflictException("Award event still has proposals; delete them first");
        }
        awardEventRepository.delete(event);
    }

    public AwardEvent find(Long id) {
        return awardEventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Award event " + id + " not found"));
    }
}
