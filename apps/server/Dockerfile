# ============================================================================
# yunsie-server 生产镜像（多阶段构建）
#   - 阶段1：Maven 3.9 + JDK 21 构建 fat-jar（跳过测试；测试在 CI/本地 mvn test 执行）
#   - 阶段2：eclipse-temurin JRE 运行，非 root 用户
#   - JVM 参数经 JAVA_OPTS 环境变量配置（默认 MaxRAMPercentage 75%）
#   - Flyway 由应用启动时执行（不新增独立 migration 容器）
# ============================================================================

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# 先复制父 pom 与模块 pom，利用依赖层缓存
COPY pom.xml ./
COPY yunsie-server-common/pom.xml yunsie-server-common/
COPY yunsie-module-sys/pom.xml yunsie-module-sys/
COPY yunsie-module-user/pom.xml yunsie-module-user/
COPY yunsie-module-certificate/pom.xml yunsie-module-certificate/
COPY yunsie-module-subject/pom.xml yunsie-module-subject/
COPY yunsie-module-course/pom.xml yunsie-module-course/
COPY yunsie-module-question/pom.xml yunsie-module-question/
COPY yunsie-module-exam/pom.xml yunsie-module-exam/
COPY yunsie-module-learning-profile/pom.xml yunsie-module-learning-profile/
COPY yunsie-module-ai/pom.xml yunsie-module-ai/
COPY yunsie-module-pay/pom.xml yunsie-module-pay/
COPY yunsie-module-notify/pom.xml yunsie-module-notify/
COPY yunsie-module-promo/pom.xml yunsie-module-promo/
COPY yunsie-server-boot/pom.xml yunsie-server-boot/
RUN mvn -B -q dependency:go-offline -DskipTests || true
# 复制源码构建
COPY . .
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S yunsie && adduser -S yunsie -G yunsie
WORKDIR /app
COPY --from=build /workspace/yunsie-server-boot/target/yunsie-server-boot-*.jar app.jar
USER yunsie
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
