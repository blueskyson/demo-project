package com.example.demobackend.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auditfixture.LeakyService;
import com.example.auditfixture.UnauditedController;
import com.example.auditfixture.UnauditedEntity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Proves the audit rules actually catch violations, so they can't silently pass forever. */
class AuditArchitectureFixtureTest {

    private final JavaClasses fixtures = new ClassFileImporter()
            .importClasses(UnauditedController.class, UnauditedEntity.class, LeakyService.class);

    @Test
    void unauditedDeleteEndpointIsReported() {
        assertThatThrownBy(() -> AuditArchitectureTest.stateChangingEndpointsMustBeAudited.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauditedController.delete");
    }

    @Test
    void endpointWithoutDecisionIsReported() {
        assertThatThrownBy(() -> AuditArchitectureTest.everyEndpointMustDecideOnAuditing.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauditedController.get");
    }

    @Test
    void blankNoAuditReasonIsReported() {
        assertThatThrownBy(() -> AuditArchitectureTest.noAuditOnMethodsNeedsReason.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauditedController.search");
    }

    @Test
    void unauditedEntityIsReported() {
        assertThatThrownBy(() -> AuditArchitectureTest.everyEntityMustDecideOnAuditing.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnauditedEntity");
    }

    @Test
    void businessCodeCallingAuditInternalsIsReported() {
        assertThatThrownBy(() -> AuditArchitectureTest.businessCodeMustNotCallAuditInternals.check(fixtures))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LeakyService");
    }
}
