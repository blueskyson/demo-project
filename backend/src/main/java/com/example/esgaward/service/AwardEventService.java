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
import com.example.esgaward.openfga.RelationshipTuples;
import com.example.esgaward.repository.AwardEventRepository;
import com.example.esgaward.repository.ProposalRepository;
import com.example.esgaward.security.Permission;
import com.example.esgaward.security.RequirePermission;
import com.example.esgaward.security.ResourceId;

@Service
public class AwardEventService {

    private final AwardEventRepository awardEventRepository;
    private final ProposalRepository proposalRepository;
    private final RelationshipTuples relationshipTuples;
    private final Clock clock;

    public AwardEventService(AwardEventRepository awardEventRepository, ProposalRepository proposalRepository,
            RelationshipTuples relationshipTuples, Clock clock) {
        this.awardEventRepository = awardEventRepository;
        this.proposalRepository = proposalRepository;
        this.relationshipTuples = relationshipTuples;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @RequirePermission(Permission.AWARD_EVENT_LIST)
    public List<AwardEventDto> list() {
        Instant now = clock.instant();
        return awardEventRepository.findAllByOrderByDeadlineDesc().stream()
                .map(event -> AwardEventDto.from(event, now))
                .toList();
    }

    @Transactional(readOnly = true)
    @RequirePermission(Permission.AWARD_EVENT_READ)
    public AwardEventDto get(@ResourceId Long id) {
        return AwardEventDto.from(find(id), clock.instant());
    }

    @Transactional
    @RequirePermission(Permission.AWARD_EVENT_CREATE)
    public AwardEventDto create(AwardEventRequest request) {
        Instant now = clock.instant();
        AwardEvent event = awardEventRepository.save(
                new AwardEvent(request.name(), request.description(), request.deadline(), now));
        relationshipTuples.awardEventCreated(event);
        return AwardEventDto.from(event, now);
    }

    @Transactional
    @RequirePermission(Permission.AWARD_EVENT_UPDATE)
    public AwardEventDto update(@ResourceId Long id, AwardEventRequest request) {
        Instant now = clock.instant();
        AwardEvent event = find(id);
        Instant oldDeadline = event.getDeadline();
        event.update(request.name(), request.description(), request.deadline(), now);
        if (!oldDeadline.equals(event.getDeadline())) {
            relationshipTuples.awardEventDeadlineChanged(event);
        }
        return AwardEventDto.from(event, now);
    }

    @Transactional
    @RequirePermission(Permission.AWARD_EVENT_DELETE)
    public void delete(@ResourceId Long id) {
        AwardEvent event = find(id);
        if (proposalRepository.existsByAwardEventId(id)) {
            throw new ConflictException("Award event still has proposals; delete them first");
        }
        awardEventRepository.delete(event);
        relationshipTuples.awardEventDeleted(id);
    }

    AwardEvent find(Long id) {
        return awardEventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Award event " + id + " not found"));
    }
}
