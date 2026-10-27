package com.pydlove.workcove.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "com.pydlove.workcove",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule domainAndApplicationStayFrameworkFree =
            noClasses()
                    .that().resideInAnyPackage("..domain..", "..application..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "org.apache.ibatis..",
                            "com.baomidou..",
                            "jakarta.servlet..");

    @ArchTest
    static final ArchRule interfacesDoNotDependOnInfrastructure =
            noClasses()
                    .that().resideInAPackage("..interfaces..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");
}
