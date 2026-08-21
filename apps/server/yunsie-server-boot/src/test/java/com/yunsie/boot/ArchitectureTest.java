package com.yunsie.boot;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块依赖方向检查（ARCHITECTURE §2.5 遗留任务，Stage 1.6 收尾）：
 *   1) common 不依赖任何业务域
 *   2) learning-profile 只能经其他域 api 包访问（禁止 Entity/Mapper/Service 实现/Controller）
 *   3) 除 boot（装配点）外，任何业务域不得依赖 learning-profile（消费式叶子域）
 *   4) 业务域之间禁止跨域访问 entity/mapper/service.impl/controller（仅 api 包契约）
 * 若发现历史结构问题：只记录并报告，不顺手大规模重构已有域。
 */
class ArchitectureTest {

    private static final String[] BUSINESS_DOMAINS = {
            "com.yunsie.module.sys..",
            "com.yunsie.module.user..",
            "com.yunsie.module.certificate..",
            "com.yunsie.module.subject..",
            "com.yunsie.module.question..",
            "com.yunsie.module.exam..",
            "com.yunsie.module.course..",
            "com.yunsie.module.ai..",
            "com.yunsie.module.pay..",
            "com.yunsie.module.notify..",
            "com.yunsie.module.promo.."
    };

    private static final String[] DOMAIN_BASES = {
            "com.yunsie.module.sys",
            "com.yunsie.module.user",
            "com.yunsie.module.certificate",
            "com.yunsie.module.subject",
            "com.yunsie.module.question",
            "com.yunsie.module.exam",
            "com.yunsie.module.course",
            "com.yunsie.module.ai",
            "com.yunsie.module.pay",
            "com.yunsie.module.notify",
            "com.yunsie.module.promo",
            "com.yunsie.module.learning.profile"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void loadClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.yunsie");
    }

    @Test
    void common_doesNotDependOnBusinessDomains() {
        noClasses().that().resideInAPackage("com.yunsie.common..")
                .should().dependOnClassesThat().resideInAnyPackage("com.yunsie.module..")
                .check(classes);
    }

    @Test
    void learningProfile_accessesOtherDomainsOnlyViaApi() {
        noClasses().that().resideInAPackage("com.yunsie.module.learning.profile..")
                .should().dependOnClassesThat(otherBusinessNonApiPackages())
                .check(classes);
    }

    @Test
    void noBusinessDomain_dependsOnLearningProfile() {
        noClasses().that().resideInAnyPackage(BUSINESS_DOMAINS)
                .should().dependOnClassesThat().resideInAPackage("com.yunsie.module.learning.profile..")
                .check(classes);
    }

    @Test
    void noCrossDomainInternalAccess() {
        noClasses().that().resideInAnyPackage(withLearningProfile(BUSINESS_DOMAINS))
                .should(notAccessOtherDomainsInternals())
                .check(classes);
    }

    /** 其他业务域的非 api 包（learning-profile 禁止访问；不含本域自身） */
    private static DescribedPredicate<JavaClass> otherBusinessNonApiPackages() {
        return new DescribedPredicate<>("other business domains' packages except api packages") {
            @Override
            public boolean test(JavaClass javaClass) {
                String pkg = javaClass.getPackageName();
                if (!pkg.startsWith("com.yunsie.module.")) {
                    return false;
                }
                if (pkg.contains(".api.") || pkg.endsWith(".api")) {
                    return false;
                }
                for (String base : DOMAIN_BASES) {
                    if (base.equals("com.yunsie.module.learning.profile")) {
                        continue; // 本域自身包不在此规则范围
                    }
                    if (pkg.startsWith(base + ".")) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /** 跨域内部包访问禁止：目标包属于其他业务域且为 entity/mapper/service.impl/controller */
    private static ArchCondition<JavaClass> notAccessOtherDomainsInternals() {
        return new ArchCondition<>("not access other business domains' internal packages "
                + "(entity/mapper/service.impl/controller); cross-domain access must go through api packages only") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                String ownDomain = domainOf(clazz.getPackageName());
                if (ownDomain == null) {
                    return;
                }
                for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetPackage = target.getPackageName();
                    if (!targetPackage.startsWith("com.yunsie.module.")) {
                        continue;
                    }
                    String targetDomain = domainOf(targetPackage);
                    if (targetDomain == null || targetDomain.equals(ownDomain)) {
                        continue;
                    }
                    if (isInternalPackage(targetPackage)) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + " 访问其他域内部包: " + target.getName()));
                    }
                }
            }
        };
    }

    private static String[] withLearningProfile(String[] domains) {
        String[] all = new String[domains.length + 1];
        System.arraycopy(domains, 0, all, 0, domains.length);
        all[domains.length] = "com.yunsie.module.learning.profile..";
        return all;
    }

    /** 内部包判定：entity / mapper / service.impl / controller（含末尾段与中段两种形态） */
    private static boolean isInternalPackage(String pkg) {
        return pkg.contains(".entity.") || pkg.endsWith(".entity")
                || pkg.contains(".mapper.") || pkg.endsWith(".mapper")
                || pkg.contains(".service.impl.") || pkg.endsWith(".service.impl")
                || pkg.contains(".controller.") || pkg.endsWith(".controller");
    }

    /** 取包所属业务域（learning.profile 为两段域名）；非业务域返回 null */
    private static String domainOf(String packageName) {
        String prefix = "com.yunsie.module.";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String rest = packageName.substring(prefix.length());
        String[] parts = rest.split("\\.");
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        if ("learning".equals(parts[0]) && parts.length > 1) {
            return "learning.profile";
        }
        return parts[0];
    }
}
