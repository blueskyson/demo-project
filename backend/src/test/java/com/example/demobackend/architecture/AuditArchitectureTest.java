package com.example.demobackend.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.example.demobackend.audit.annotation.AuditIgnore;
import com.example.demobackend.audit.annotation.Audited;
import com.example.demobackend.audit.annotation.AuditedEntity;
import com.example.demobackend.audit.annotation.NoAudit;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;

import com.tngtech.archunit.core.domain.properties.HasSourceCodeLocation;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reminds developers where auditing is required. A failing rule here means a new endpoint
 * or entity was added without deciding how it's audited.
 */
@AnalyzeClasses(packages = "com.example.demobackend", importOptions = ImportOption.DoNotIncludeTests.class)
public class AuditArchitectureTest {

    private static final DescribedPredicate<HasAnnotations<?>> STATE_CHANGING_MAPPING =
            DescribedPredicate.describe("state-changing request mappings",
                    method -> method.isAnnotatedWith(PostMapping.class) || method.isAnnotatedWith(PutMapping.class)
                            || method.isAnnotatedWith(PatchMapping.class) || method.isAnnotatedWith(DeleteMapping.class));

    @ArchTest
    public static final ArchRule stateChangingEndpointsMustBeAudited = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and(STATE_CHANGING_MAPPING)
            .should().beAnnotatedWith(Audited.class)
            .because("every POST/PUT/PATCH/DELETE request must leave an audit trail; @NoAudit is not allowed here");

    @ArchTest
    public static final ArchRule everyEndpointMustDecideOnAuditing = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().areMetaAnnotatedWith(RequestMapping.class)
            .should().beAnnotatedWith(Audited.class).orShould().beAnnotatedWith(NoAudit.class)
            .because("reads of sensitive data may need auditing too, so every endpoint must opt in or out explicitly");

    @ArchTest
    public static final ArchRule everyEntityMustDecideOnAuditing = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().beAnnotatedWith(AuditedEntity.class).orShould().beAnnotatedWith(NoAudit.class)
            .because("field changes of new entities must be audited unless explicitly opted out");

    @ArchTest
    public static final ArchRule noAuditOnMethodsNeedsReason = methods()
            .that().areAnnotatedWith(NoAudit.class)
            .should(haveNonBlankNoAuditReason());

    @ArchTest
    public static final ArchRule noAuditOnClassesNeedsReason = classes()
            .that().areAnnotatedWith(NoAudit.class)
            .should(haveNonBlankNoAuditReason())
            .allowEmptyShould(true); // no entity is opted out yet

    @ArchTest
    public static final ArchRule timestampFieldsAreNotAudited = fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(AuditedEntity.class)
            .and().haveNameMatching("(created|updated|modified|lastModified)(At|Date|Time|On)?")
            .should().beAnnotatedWith(CreationTimestamp.class)
            .orShould().beAnnotatedWith(UpdateTimestamp.class)
            .orShould().beAnnotatedWith(AuditIgnore.class)
            .orShould().beAnnotatedWith(Version.class)
            .because("bookkeeping timestamps change on every write and are excluded from field-change auditing");

    @ArchTest
    public static final ArchRule auditedOnlyOnControllers = methods()
            .that().areAnnotatedWith(Audited.class)
            .should().beDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .because("the audit aspect must wrap the whole request, outside the service transaction");

    @ArchTest
    public static final ArchRule businessCodeMustNotCallAuditInternals = noClasses()
            .that().resideOutsideOfPackage("..audit..")
            .should().dependOnClassesThat(resideInAPackage("..audit..").and(not(resideInAPackage("..audit.annotation.."))))
            .because("auditing is declarative: business code may only use the annotations in audit.annotation");

    private static <T extends HasAnnotations<?> & HasDescription & HasSourceCodeLocation> ArchCondition<T> haveNonBlankNoAuditReason() {
        return new ArchCondition<>("have a non-blank @NoAudit reason") {
            @Override
            public void check(T item, ConditionEvents events) {
                Object reason = item.getAnnotationOfType(NoAudit.class.getName()).get("reason").orElse("");
                boolean blank = reason.toString().isBlank();
                events.add(new SimpleConditionEvent(item, !blank,
                        item.getDescription() + " has a blank @NoAudit reason in " + item.getSourceCodeLocation()));
            }
        };
    }
}
