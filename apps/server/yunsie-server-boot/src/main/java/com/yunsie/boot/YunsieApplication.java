package com.yunsie.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用装配入口（server-boot 只做装配，不写业务逻辑）。
 *
 * <p>组件扫描覆盖 {@code com.yunsie} 全部业务域；
 * 业务域之间的调用只允许走各模块 {@code api} 包，见 docs/ARCHITECTURE.md。</p>
 *
 * <p>Mapper 扫描显式按域登记：新增业务域的 Mapper 时在此追加对应包（避免全量通配扫描）。</p>
 *
 * <p>{@code @EnableAsync}：learning-profile 学习事件异步消费（进程内，无 MQ，见 CONFLICTS #18）。</p>
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.yunsie")
@EnableScheduling
@EnableAsync
@MapperScan({"com.yunsie.module.sys.mapper", "com.yunsie.module.user.mapper",
        "com.yunsie.module.certificate.mapper", "com.yunsie.module.subject.mapper",
        "com.yunsie.module.question.mapper", "com.yunsie.module.exam.mapper",
        "com.yunsie.module.course.mapper", "com.yunsie.module.learning.profile.mapper",
        "com.yunsie.module.notify.mapper"})
public class YunsieApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunsieApplication.class, args);
    }
}
