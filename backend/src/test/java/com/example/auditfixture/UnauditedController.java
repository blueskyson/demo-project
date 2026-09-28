package com.example.auditfixture;

import com.example.demobackend.audit.annotation.NoAudit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately violates the audit rules; only used by {@code AuditArchitectureFixtureTest}.
 * Lives outside {@code com.example.demobackend} so Spring never scans it.
 */
@RestController
public class UnauditedController {

    @DeleteMapping("/fixture/{id}")
    public void delete(@PathVariable Long id) {
    }

    @GetMapping("/fixture/{id}")
    public String get(@PathVariable Long id) {
        return "";
    }

    @NoAudit(reason = " ")
    @GetMapping("/fixture")
    public String search() {
        return "";
    }
}
