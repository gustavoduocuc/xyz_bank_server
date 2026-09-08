package cl.duoc.xyzbank.bffmobile;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cl.duoc.xyzbank.bffmobile", importOptions = ImportOption.DoNotIncludeTests.class)
class BffMobileArchitectureTest {

    @ArchTest
    static final ArchRule doesNotImportPersistenceTypes = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "org.springframework.data.jpa..");

    @ArchTest
    static final ArchRule doesNotImportCoreDomain = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("cl.duoc.xyzbank.coredomain..");

    @ArchTest
    static final ArchRule doesNotImportOtherBffs = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("cl.duoc.xyzbank.bffweb..", "cl.duoc.xyzbank.bffatm..");
}
