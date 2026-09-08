package cl.duoc.xyzbank.bffatm;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cl.duoc.xyzbank.bffatm", importOptions = ImportOption.DoNotIncludeTests.class)
class BffAtmArchitectureTest {

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
            .resideInAnyPackage("cl.duoc.xyzbank.bffweb..", "cl.duoc.xyzbank.bffmobile..");

    @ArchTest
    static final ArchRule exposesOnlyBalanceAndWithdrawalControllers = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleName("BalanceController")
            .orShould()
            .haveSimpleName("WithdrawalController")
            .orShould()
            .haveSimpleName("OpenApiDocumentController");

    @ArchTest
    static final ArchRule useCasesControllersAndAdaptersDoNotReadServletIdentityHeaders = noClasses()
            .that()
            .resideInAnyPackage("..application..", "..infrastructure.adapters..")
            .or()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("jakarta.servlet.http.HttpServletRequest");

    @ArchTest
    static final ArchRule useCasesControllersAndAdaptersObtainIdentityThroughCallerContext = noClasses()
            .that()
            .resideInAnyPackage("..application..", "..infrastructure.adapters..")
            .or()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("cl.duoc.xyzbank.sharedsecurity.callercontext.HeaderCallerContextAdapter");
}
