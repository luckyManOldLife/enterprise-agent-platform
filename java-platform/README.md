# Java Platform

Java 主平台。目录模块先按边界建立，首期采用模块化单体，后续根据吞吐和团队边界再拆服务。

## 本地开发

本模块使用项目内 Maven Wrapper，不要求在云服务器或开发机全局安装 Maven。
本机只需要 JDK 21；首次启动 Wrapper 还需要 `curl` 或 `wget`，以及 `unzip`。
PostgreSQL、Redis 等基础服务继续通过 Docker Compose 启动。

```bash
# Ubuntu 24.04 example: install JDK only, not Maven
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk curl unzip

cd java-platform

# 首次运行会把 Maven 发行版下载到 ~/.m2/wrapper/dists
./mvnw test

# 只测试核心模块
./mvnw -pl platform-domain,platform-application,platform-api,policy-engine,task-service test
```

如果需要复用已有 Docker 基础设施，请使用 `deploy/compose/docker-compose.infra.yml`，
不要在主机上裸装 PostgreSQL 或 Redis。

空间紧张时优先清理构建产物和 Maven 依赖缓存：

```bash
./mvnw clean
rm -rf ~/.m2/repository ~/.m2/wrapper/dists
```
