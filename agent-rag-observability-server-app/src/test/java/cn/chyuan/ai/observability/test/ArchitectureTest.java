package cn.chyuan.ai.observability.test;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * AUTOLOOP al-06 / 工单 1006：七模块分层架构守卫（借鉴 archunit/ArchUnit）。
 *
 * <p>分层不变量按 2026-09 现实校准（import 扫描结论）：
 * types→无；domain→types；api→types；client→types；infrastructure→domain+types；
 * trigger→api+domain+infrastructure+types；app 模块为组装根不入层。
 * 与 aggregation 仓不同构：本仓 api 依赖 types 而非 domain。
 *
 * <p>校准经验承 al-03：层模式锚定前缀防误圈第三方包；无环切片到模块一级；
 * 不做「domain 禁 spring」规则（模板风格）。
 */
@AnalyzeClasses(packages = "cn.chyuan.ai.observability", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule 七模块依赖方向 = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Types").definedBy("cn.chyuan.ai.observability.types..")
            .layer("Domain").definedBy("cn.chyuan.ai.observability.domain..")
            .layer("Api").definedBy("cn.chyuan.ai.observability.api..")
            .layer("Client").definedBy("cn.chyuan.ai.observability.client..")
            .layer("Infrastructure").definedBy("cn.chyuan.ai.observability.infrastructure..")
            .layer("Trigger").definedBy("cn.chyuan.ai.observability.trigger..")
            .whereLayer("Types").mayNotAccessAnyLayer()
            .whereLayer("Domain").mayOnlyAccessLayers("Types")
            .whereLayer("Api").mayOnlyAccessLayers("Types")
            .whereLayer("Client").mayOnlyAccessLayers("Types")
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Types")
            .whereLayer("Trigger").mayOnlyAccessLayers("Api", "Domain", "Infrastructure", "Types");

    // 无环粒度到模块一级（(*)）：模块内部包间相互引用不属跨模块腐化
    @ArchTest
    static final ArchRule 模块包无环 = SlicesRuleDefinition.slices()
            .matching("cn.chyuan.ai.observability.(*)").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule Web入口归位 = noClasses()
            .that().resideOutsideOfPackage("..trigger..")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.Controller");
}
