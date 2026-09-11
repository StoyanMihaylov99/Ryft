package com.application.ryft;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final String BASE_PACKAGE = "com.application.ryft";

    // Keep in sync with the module list in docs/ARCHITECTURE.md.
    private static final String[] MODULES = {
            "identity", "projects", "issues", "workflow", "sprints",
            "notifications", "activity", "search", "admin"
    };

    @Test
    void modulesOnlyExposeTheirRepositoriesToThemselves() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        for (String module : MODULES) {
            String modulePackage = BASE_PACKAGE + "." + module;

            noClasses()
                    .that().resideOutsideOfPackage(modulePackage + "..")
                    .should().dependOnClassesThat()
                    // ".." matches zero-or-more path segments, so this covers both a flat
                    // "<module>.repository" package and one nested under a concept sub-package
                    // (e.g. "identity.workspace.repository").
                    .resideInAPackage(modulePackage + "..repository..")
                    .because("other modules may only reach the '" + module
                            + "' module through its public *Service interface, "
                            + "never its repository (or the JPA entities behind it) directly")
                    .check(classes);
        }
    }
}
