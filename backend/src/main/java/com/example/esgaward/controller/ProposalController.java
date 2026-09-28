package com.example.esgaward.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.esgaward.dto.AddMemberRequest;
import com.example.esgaward.dto.CreateProposalRequest;
import com.example.esgaward.dto.ProposalDetailDto;
import com.example.esgaward.dto.ProposalSummaryDto;
import com.example.esgaward.dto.UpdateProposalRequest;
import com.example.esgaward.service.ProposalService;

@RestController
@RequestMapping("/api")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @GetMapping("/proposals")
    public List<ProposalSummaryDto> list(@RequestParam(required = false) Long awardEventId) {
        return proposalService.list(awardEventId);
    }

    @GetMapping("/proposals/{id}")
    public ProposalDetailDto get(@PathVariable Long id) {
        return proposalService.get(id);
    }

    @PostMapping("/award-events/{awardEventId}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalDetailDto create(@PathVariable Long awardEventId,
            @Valid @RequestBody CreateProposalRequest request) {
        return proposalService.create(awardEventId, request);
    }

    @PutMapping("/proposals/{id}")
    public ProposalDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateProposalRequest request) {
        return proposalService.update(id, request);
    }

    @DeleteMapping("/proposals/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        proposalService.delete(id);
    }

    @PostMapping("/proposals/{id}/members")
    public ProposalDetailDto addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        return proposalService.addMember(id, request);
    }

    @DeleteMapping("/proposals/{id}/members/{userId}")
    public ProposalDetailDto removeMember(@PathVariable Long id, @PathVariable UUID userId) {
        return proposalService.removeMember(id, userId);
    }
}
