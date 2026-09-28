package com.example.esgaward.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;

import com.example.esgaward.architecture.fixture.service.UnannotatedService;
import com.example.esgaward.security.Permission;
import com.example.esgaward.security.RequirePermission;
import com.example.esgaward.security.ResourceId;
import com.example.esgaward.security.ResourceType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Makes authorization coverage a build failure instead of a latent security hole: every public
 * service method must declare a {@link RequirePermission}, which {@code PermissionAspect} enforces.
 */
@AnalyzeClasses(packages = "com.example.esgaward", importOptions = ImportOption.DoNotIncludeTests.class)
class ServiceArchitectureTest {

    @ArchTest
    static final ArchRule public_service_methods_must_require_a_permission =
            methods().that().areDeclaredInClassesThat().resideInAPackage("..service..")
                    .and().arePublic()
                    .should().beAnnotatedWith(RequirePermission.class)
                    .because("every service entry point must declare the permission it needs; "
                            + "make helpers non-public or move them out of the service package");

    @ArchTest
    static final ArchRule permissions_are_only_declared_on_public_service_methods =
            methods().that().areAnnotatedWith(RequirePermission.class)
                    .should().beDeclaredInClassesThat().resideInAPackage("..service..")
                    .andShould().bePublic()
                    .because("PermissionAspect only intercepts calls through the Spring proxy, "
                            + "so the annotation must sit on public service entry points");

    @ArchTest
    static final ArchRule resource_id_parameter_must_match_permission =
            methods().that().areAnnotatedWith(RequirePermission.class)
                    .should(declareResourceIdMatchingPermission());

    @ArchTest
    static final ArchRule no_spel_method_security_on_methods =
            noMethods().should().beAnnotatedWith(PreAuthorize.class)
                    .orShould().beAnnotatedWith(PostAuthorize.class)
                    .orShould().beAnnotatedWith(Secured.class)
                    .because("authorization is declared with the strongly typed @RequirePermission");

    @ArchTest
    static final ArchRule no_spel_method_security_on_classes =
            noClasses().should().beAnnotatedWith(PreAuthorize.class)
                    .orShould().beAnnotatedWith(PostAuthorize.class)
                    .orShould().beAnnotatedWith(Secured.class)
                    .because("authorization is declared with the strongly typed @RequirePermission");

    /** Guards the guard: the main rule really does flag a public service method without a permission. */
    @Test
    void ruleRejectsServiceMethodWithoutPermission() {
        var classes = new ClassFileImporter().importClasses(UnannotatedService.class);

        assertThatThrownBy(() -> public_service_methods_must_require_a_permission.check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnannotatedService.resetPassword(java.lang.Long)")
                .hasMessageContaining("@RequirePermission");
    }

    /**
     * Resource-scoped permissions need exactly one {@code @ResourceId Long} parameter so
     * {@code PermissionAspect} knows which resource to check; {@code NONE} permissions must have none.
     */
    private static ArchCondition<JavaMethod> declareResourceIdMatchingPermission() {
        return new ArchCondition<>("have a @ResourceId Long parameter exactly when the permission targets a resource") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Permission permission = method.getAnnotationOfType(RequirePermission.class).value();
                List<JavaParameter> ids = method.getParameters().stream()
                        .filter(p -> p.isAnnotatedWith(ResourceId.class))
                        .toList();
                boolean resourceScoped = permission.resourceType() != ResourceType.NONE;

                String problem = null;
                if (resourceScoped && ids.size() != 1) {
                    problem = "needs exactly one @ResourceId parameter because " + permission + " targets "
                            + permission.resourceType();
                } else if (!resourceScoped && !ids.isEmpty()) {
                    problem = "must not have a @ResourceId parameter because " + permission + " is not resource-scoped";
                } else if (resourceScoped && !ids.get(0).getRawType().isEquivalentTo(Long.class)) {
                    problem = "@ResourceId parameter must be a Long";
                }
                if (problem != null) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName() + " " + problem));
                }
            }
        };
    }
}
