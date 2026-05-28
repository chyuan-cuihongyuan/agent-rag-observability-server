# Agent RAG Observability Server — Dockerfile
# 多阶段构建：先在 Maven 容器中构建，再打包到轻量级运行时镜像

# ==================== 阶段1: Maven 构建 ====================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# 复制父 POM 和各模块 POM（利用 Docker 缓存层）
COPY pom.xml .
COPY agent-rag-observability-server-api/pom.xml agent-rag-observability-server-api/
COPY agent-rag-observability-server-types/pom.xml agent-rag-observability-server-types/
COPY agent-rag-observability-server-domain/pom.xml agent-rag-observability-server-domain/
COPY agent-rag-observability-server-infrastructure/pom.xml agent-rag-observability-server-infrastructure/
COPY agent-rag-observability-server-trigger/pom.xml agent-rag-observability-server-trigger/
COPY agent-rag-observability-server-client/pom.xml agent-rag-observability-server-client/
COPY agent-rag-observability-server-app/pom.xml agent-rag-observability-server-app/

# 下载依赖（利用 Docker 缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY agent-rag-observability-server-api/src agent-rag-observability-server-api/src
COPY agent-rag-observability-server-types/src agent-rag-observability-server-types/src
COPY agent-rag-observability-server-domain/src agent-rag-observability-server-domain/src
COPY agent-rag-observability-server-infrastructure/src agent-rag-observability-server-infrastructure/src
COPY agent-rag-observability-server-trigger/src agent-rag-observability-server-trigger/src
COPY agent-rag-observability-server-client/src agent-rag-observability-server-client/src
COPY agent-rag-observability-server-app/src agent-rag-observability-server-app/src

# 构建并跳过测试（生产环境可加上测试）
RUN mvn clean package -DskipTests -B

# ==================== 阶段2: 运行时镜像 ====================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="chyuan.ai"
LABEL description="Agent RAG Observability Server"

# 安装 curl 用于健康检查
RUN apk add --no-cache curl

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/agent-rag-observability-server-app/target/*.jar app.jar

# 创建非 root 用户
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 暴露端口
EXPOSE 8092

# JVM 参数优化
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:+UseStringDeduplication"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8092/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
