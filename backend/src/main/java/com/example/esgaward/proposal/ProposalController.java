package com.example.esgaward.proposal;

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

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @GetMapping
    public List<ProposalSummaryDto> list(@RequestParam(required = false) Long awardEventId) {
        return proposalService.list(awardEventId);
    }

    @GetMapping("/{id}")
    public ProposalDetailDto get(@PathVariable Long id) {
        return proposalService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalDetailDto create(@Valid @RequestBody CreateProposalRequest request) {
        return proposalService.create(request);
    }

    @PutMapping("/{id}")
    public ProposalDetailDto update(@PathVariable Long id, @Valid @RequestBody UpdateProposalRequest request) {
        return proposalService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        proposalService.delete(id);
    }

    @PostMapping("/{id}/members")
    public ProposalDetailDto addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        return proposalService.addMember(id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ProposalDetailDto removeMember(@PathVariable Long id, @PathVariable UUID userId) {
        return proposalService.removeMember(id, userId);
    }
}
