package com.example.esgaward.awardevent;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/award-events")
public class AwardEventController {

    private final AwardEventService awardEventService;

    public AwardEventController(AwardEventService awardEventService) {
        this.awardEventService = awardEventService;
    }

    @GetMapping
    public List<AwardEventDto> list() {
        return awardEventService.list();
    }

    @GetMapping("/{id}")
    public AwardEventDto get(@PathVariable Long id) {
        return awardEventService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AwardEventDto create(@Valid @RequestBody AwardEventRequest request) {
        return awardEventService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AwardEventDto update(@PathVariable Long id, @Valid @RequestBody AwardEventRequest request) {
        return awardEventService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        awardEventService.delete(id);
    }
}
