# 运行时镜像：直接装已打好的 Spring Boot 可执行 jar
# 本地策略：不在容器里跑 gradle（避开本机缺 JDK21 + 构建期代理问题）。
# CI 正规做法是多阶段构建（gradle 编译 → JRE 运行），见 docs/k8s/06-概念与原理.md 备注。

# 基础镜像：Java 21 的 JRE（只含运行时，比 JDK 小）。宿主机 docker 会走代理从 Hub 拉。
FROM eclipse-temurin:21-jre

WORKDIR /app

# 拷入可执行 bootJar（44M 那个；不是 37K 的 -plain.jar）
# 版本号变了记得同步改这里
COPY build/libs/iot-platform-0.0.1-SNAPSHOT.jar app.jar

# 容器时区，避免日志/时间差 8 小时
ENV TZ=Asia/Shanghai

# 声明端口（仅文档作用）：8080 web / 9000 Netty TCP / 1502 Modbus
EXPOSE 8080 9000 1502

# exec 形式 → java 进程直接当 PID 1，能收 SIGTERM 优雅停机（见 06 文档）
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
