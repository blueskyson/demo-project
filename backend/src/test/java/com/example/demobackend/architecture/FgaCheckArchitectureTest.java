package com.example.demobackend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.example.demobackend.authorization.FgaCheck;
import com.example.demobackend.authorization.NoFgaCheck;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Turns "forgot to add {@code @FgaCheck}" into a build failure instead of a silent gap:
 * every public method on a {@code @RestController} or {@code @Service} (outside the
 * authorization/config plumbing packages, which implement the checks rather than needing
 * them) must declare either {@link FgaCheck} or an explicit {@link NoFgaCheck} opt-out.
 */
class FgaCheckArchitectureTest {

    private static final String BASE_PACKAGE = "com.example.demobackend";

    private static final DescribedPredicate<JavaMethod> IS_PROTECTABLE_CONTROLLER_OR_SERVICE_METHOD =
            DescribedPredicate.describe(
                    "a public method declared in a @RestController or @Service outside "
                            + "the authorization/config packages",
                    method -> {
                        JavaClass owner = method.getOwner();
                        boolean isControllerOrService = owner.isAnnotatedWith(RestController.class)
                                || owner.isAnnotatedWith(Service.class);
                        boolean isPlumbing = owner.getPackageName().startsWith(BASE_PACKAGE + ".authorization")
                                || owner.getPackageName().startsWith(BASE_PACKAGE + ".config");
                        return isControllerOrService && !isPlumbing
                                && method.getModifiers().contains(JavaModifier.PUBLIC);
                    });

    private static final ArchCondition<JavaMethod> DECLARE_FGA_CHECK_OR_OPT_OUT =
            new ArchCondition<>("be annotated with @FgaCheck or @NoFgaCheck") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    boolean satisfied =
                            method.isAnnotatedWith(FgaCheck.class) || method.isAnnotatedWith(NoFgaCheck.class);
                    events.add(new SimpleConditionEvent(method, satisfied,
                            method.getFullName()
                                    + " is missing @FgaCheck or @NoFgaCheck — every Controller/Service method "
                                    + "must declare one so a new endpoint can't ship unprotected by accident"));
                }
            };

    @Test
    void everyControllerAndServiceMethodDeclaresAuthorizationIntent() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        ArchRule rule = methods()
                .that(IS_PROTECTABLE_CONTROLLER_OR_SERVICE_METHOD)
                .should(DECLARE_FGA_CHECK_OR_OPT_OUT);

        rule.check(importedClasses);
    }
}
