package cl.duoc.xyzbank.authserver;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cl.duoc.xyzbank.authserver", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthServerArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnlyOnDomainAndTheChannelModel = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..domain..", "cl.duoc.xyzbank.sharedsecurity.callercontext..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDependsOnlyOnApplicationAndDomain = classes()
            .that()
            .resideInAPackage("..application..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..", "..domain..", "..application..", "cl.duoc.xyzbank.sharedsecurity.callercontext..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainAndApplicationAreFrameworkFree = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "com.nimbusds..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule doesNotImportPersistenceTypes = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "org.springframework.data.jpa..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule doesNotImportOtherDeployables = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "cl.duoc.xyzbank.coredomain..",
                    "cl.duoc.xyzbank.coreservice..",
                    "cl.duoc.xyzbank.bffweb..",
                    "cl.duoc.xyzbank.bffmobile..",
                    "cl.duoc.xyzbank.bffatm..")
            .allowEmptyShould(true);
}
