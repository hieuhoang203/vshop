package vn.vshop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "vn.vshop", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {
    @ArchTest
    static final ArchRule controllers_use_services_not_storage =
            noClasses()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..repository..", "org.springframework.jdbc..", "java.sql..");

    @ArchTest
    static final ArchRule only_repositories_use_jdbc =
            noClasses()
                    .that()
                    .resideOutsideOfPackage("..repository..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("org.springframework.jdbc..", "java.sql..");

    @ArchTest
    static final ArchRule services_do_not_depend_on_http_or_dtos =
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..controller..",
                            "..dto..",
                            "..repository.jdbc..",
                            "org.springframework.web..",
                            "org.springframework.http..",
                            "jakarta.servlet..");

    @ArchTest
    static final ArchRule repositories_do_not_depend_on_upper_layers =
            noClasses()
                    .that()
                    .resideInAPackage("..repository..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..controller..", "..service..", "..dto..", "..mapper..");

    @ArchTest
    static final ArchRule entities_are_framework_independent =
            noClasses()
                    .that()
                    .resideInAPackage("..entity..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..dto..",
                            "..repository..",
                            "..controller..",
                            "org.springframework..",
                            "jakarta.servlet..",
                            "java.sql..");

    @ArchTest
    static final ArchRule jdbc_adapters_are_repositories =
            classes()
                    .that()
                    .resideInAPackage("..repository.jdbc..")
                    .and()
                    .areTopLevelClasses()
                    .should()
                    .beAnnotatedWith(Repository.class);

    @ArchTest
    static final ArchRule controllers_are_rest_controllers =
            classes()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .beAnnotatedWith(RestController.class);

    @ArchTest
    static final ArchRule business_modules_are_acyclic =
            slices().matching("vn.vshop.modules.(*)..").should().beFreeOfCycles();
}
