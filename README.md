# DbShift

轻量级**数据库变更框架**，面向小团队。纯 Java 库，可嵌入任意 Java 项目或通过 Spring Boot Starter 集成。

框架本身**不绑定任何数据库**——通过标准 JDBC 连接，由使用方提供对应 JDBC 驱动和迁移 SQL。

## 模块

| 模块 | 说明 |
|------|------|
| `dbshift-core` | 迁移引擎框架，提供 `DbshiftEngine` API（无 JDBC 驱动依赖） |
| `dbshift-spring-boot-starter` | Spring Boot 启动时自动迁移（可选） |

## 支持的数据库

通过 JDBC URL **自动识别**方言，也可在配置中显式指定 `dialect`：

| 数据库 | JDBC URL 前缀 | 显式 dialect |
|--------|---------------|--------------|
| MySQL | `jdbc:mysql:` | `mysql` |
| MariaDB | `jdbc:mariadb:` | `mariadb` |
| PostgreSQL | `jdbc:postgresql:` | `postgresql` |
| H2 | `jdbc:h2:` | `h2` |
| SQLite | `jdbc:sqlite:` | `sqlite` |
| SQL Server | `jdbc:sqlserver:` | `sqlserver` |
| Oracle | `jdbc:oracle:` | `oracle` |
| 其他 | 任意 JDBC URL | `generic`（通用 ANSI SQL） |

迁移脚本（`V001__*.sql`）由你按目标数据库编写，与 Flyway / Liquibase 一样由使用方负责方言差异。

## 功能

- SQL 文件版本化管理（`V001__xxx.sql` / `U001__xxx.sql`）
- `schema_changelog` 版本追踪 + checksum 防篡改
- Dry-run 执行计划（Markdown / HTML 报告）
- 回滚预览与执行（需手写 U 脚本）
- 多环境版本 diff + schema diff（基于 JDBC 元数据，数据库无关）
- SQL 影响分析与风险等级标注（按方言提示 DDL 事务行为）

## 与其他框架对比

DbShift 的定位是**小团队的轻量 Java 迁移库**，不是 Flyway / Liquibase 的完全替代品。优势集中在「够用、好嵌、好审、好控」，而不是生态规模或企业级功能广度。

### 定位一览

| 框架 | 典型定位 |
|------|----------|
| **Flyway** | 行业标准，SQL 版本化，生态成熟 |
| **Liquibase** | 企业级变更管理，XML/YAML changelog，功能最全 |
| **DbShift** | 轻量嵌入库，SQL 直写 + 多环境治理 + 上线前可审可控 |

### 核心优势

**1. 极轻、零绑定，真正数据库无关**

- `dbshift-core` 只依赖 JSqlParser + SnakeYAML，不依赖 Spring，不捆绑任何 JDBC 驱动
- 通过 `DatabaseDialect` 自动适配多种数据库；换库只换驱动和 SQL，不换框架

**2. 一等公民 Java API，而不是「CLI 工具 + 集成层」**

DbShift 从设计上就是可嵌入的库，适合自建发布流水线、内嵌运维平台、在业务代码里精确控制迁移时机：

```java
DbshiftEngine engine = DbshiftEngine.fromYaml(Path.of("dbshift.yaml"));
engine.plan("staging", null, true);                        // 生成计划
engine.migrate("dev", null, false, true, "ci-bot");        // 执行迁移
engine.diffEnvironments(true, "dev", "prod");              // 跨环境对比
```

Flyway / Liquibase 以 CLI / Maven / Gradle 插件为主，程序化调用是附加能力。

**3. 多环境治理是内置能力**

一个 `dbshift.yaml` 同时管理 dev / staging / prod，并内置：

- 跨环境**版本 diff**（谁落后、谁有 pending）
- 跨环境 **schema diff**（表/字段级差异，基于 JDBC 元数据）

Flyway / Liquibase 通常需按环境拆配置或靠 CI 脚本拼装。

**4. 上线前「可审」——不只是 dry-run 日志**

`plan` 产出结构化计划，支持 Markdown / HTML 报告，包含待执行脚本、SQL 影响分析、风险等级，以及按方言提示的 DDL 事务行为（如 MySQL DDL 可能 auto-commit）。对坚持手写 SQL 的团队，评审人直接看到将执行的 SQL 及风险。

**5. 生产环境安全阀是默认设计**

`requireConfirmForProd: true` 时，prod 环境默认跳过自动迁移，必须显式调用 `engine.migrate(..., true, true, ...)`，避免应用启动误改生产库。

**6. 回滚模型简单、可预期**

- 正向：`V001__xxx.sql`，回滚：`U001__xxx.sql`（与 V 编号一一对应）
- 支持回滚预览（`rollbackPlan`）和显式确认后执行
- Flyway 社区版无通用 undo；Liquibase 支持 rollback 但依赖 changelog 模型，学习成本更高

**7. 防篡改与可追溯**

已执行脚本带 checksum，改动历史 SQL 会校验失败；changelog 记录 `applied_by`、`execution_ms`、`applied_at`，表结构简单透明。

**8. Spring Boot 集成可选，不绑架技术栈**

纯 Java 用 `DbshiftBootstrap.start(...)`，Spring Boot 加 starter 即可——非 Spring 项目、微服务内嵌同样适用。

### 逐项对比

| 维度 | DbShift | Flyway | Liquibase |
|------|---------|--------|-----------|
| 学习成本 | 低（SQL + V/U 命名） | 低 | 中高（changelog 模型） |
| 核心依赖重量 | 很轻 | 中等 | 较重 |
| JDBC 驱动 | 使用方提供 | 使用方提供 | 使用方提供 |
| 变更表达方式 | 纯 SQL 文件 | 纯 SQL 文件 | SQL / XML / YAML / JSON |
| 程序化 API | **核心能力** | 有，非首要 | 有，非首要 |
| 多环境 diff | **内置** | 需自建 | 需自建 |
| Schema diff | **内置** | 无（需第三方） | 有部分能力 |
| SQL 风险分析 | **内置** | 无 | 有限 |
| 执行计划报告 | **Markdown / HTML** | 基础 | 中等 |
| 社区 / 生态 | 新、小 | 最大 | 大 |
| 企业功能 | 无 | 商业版（undo 等） | 商业版 |
| 自动生成回滚 | 无 | 无（社区版） | 部分支持 |
| 适合团队规模 | 小团队 | 中小到大型 | 中大型企业 |

### 选型建议

**适合选 DbShift：**

- 想要 Flyway 的简洁，还需要多环境对比、上线评审报告、生产防呆
- 不想引入 Liquibase 的 changelog 抽象，坚持 SQL 即真相
- 需要在自研 CI/CD、运维平台、Java 服务里深度嵌入迁移逻辑
- 项目可能换数据库，希望框架不绑驱动、不绑厂商
- 非 Spring 或轻量 Spring 项目，不想单独维护一套 CLI / 插件体系

**不太适合选 DbShift：**

- 需要成熟商业支持、大量现成插件、活跃社区问答
- 需要 Liquibase 式 XML 管理 schema、自动 diff 生成变更
- 需要 Flyway 商业版的 undo、大规模团队协作规范
- 非 Java 技术栈，只想用独立 CLI 工具

**总结：** DbShift 的优势不是「功能比 Flyway / Liquibase 更多」，而是更轻、更透、更审、更控、更自由。只需版本化执行 SQL 选 Flyway；需要企业级变更编排选 Liquibase；需要轻量 + 可嵌入 + 多环境可审可控，DbShift 的差异化就在这里。

## 作为框架嵌入

Maven 依赖：

```xml
<dependency>
    <groupId>com.dbshift</groupId>
    <artifactId>dbshift-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 运行时由使用方提供 JDBC 驱动，例如： -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

代码示例：

```java
import com.dbshift.DbshiftEngine;
import java.nio.file.Path;

DbshiftEngine engine = DbshiftEngine.fromYaml(Path.of("dbshift.yaml"));

// 初始化版本表
engine.init("dev");

// 查看状态
var status = engine.status("dev");

// 预览执行计划
var plan = engine.plan("dev", null, true);
System.out.println(engine.reports().toMarkdownPlan(plan));

// 执行迁移
var result = engine.migrate("dev", null, false, true, "my-app");

// 校验脚本
var validation = engine.validate();
```

也可直接访问底层服务：

```java
engine.migrations();  // MigrationService
engine.reports();     // ReportGenerator
engine.config();      // DbshiftConfig
```

## 启动时自动迁移

类似 Flyway，集成后可配置为**应用启动时自动执行 pending 迁移**。

### 方式一：纯 Java 项目

`dbshift.yaml` 中开启：

```yaml
autoMigrate:
  enabled: true
  environment: dev        # 或 ${DBSHIFT_ENV:dev}
  initOnStartup: true     # 自动创建 schema_changelog 表
  failOnError: true       # 迁移失败则阻止启动
  appliedBy: my-app
  requireConfirmForProd: true   # prod 环境跳过自动迁移，需手动调用 API
```

在 `main()` 里一行调用（放在业务逻辑之前）：

```java
public static void main(String[] args) {
    DbshiftBootstrap.start(Path.of("dbshift.yaml"));
    // ... 启动你的应用
}
```

或使用 engine：

```java
DbshiftEngine.fromYaml(Path.of("dbshift.yaml")).start();
```

### 方式二：Spring Boot 项目（推荐）

添加依赖：

```xml
<dependency>
    <groupId>com.dbshift</groupId>
    <artifactId>dbshift-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<!-- 使用方提供 JDBC 驱动，例如 PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

`application.yml`：

```yaml
dbshift:
  auto-migrate: true
  config-file: ./dbshift.yaml
  environment: dev          # 可选，覆盖 yaml 中的 environment
  fail-on-error: true
```

**无需写任何代码**，Spring Boot 启动时会自动执行迁移。

### 行为说明

| 场景 | 行为 |
|------|------|
| `autoMigrate.enabled=false` | 不执行，需手动调用 `engine.migrate()` |
| 无 pending 脚本 | 启动继续，打印 "no pending migrations" |
| 校验失败 / 迁移失败 | `failOnError=true` 时抛异常，阻止启动 |
| `prod` 环境 + `requireConfirmForProd=true` | 跳过自动迁移，需手动 `engine.migrate(..., true, true, ...)` |

## 目录结构

```text
example/
├── dbshift.yaml
└── db/
    ├── migrations/
    │   ├── V001__create_user_table.sql
    │   ├── V002__add_user_email_index.sql
    │   └── V003__create_order_table.sql
    └── rollback/
        ├── U001__drop_user_table.sql
        ├── U002__drop_user_email_index.sql
        └── U003__drop_order_table.sql
```

## 构建

```bash
cd /Users/simon.ma/ownproject/ai/dbshift
mvn test
```

产物：

- `dbshift-core/target/dbshift-core-1.0.0-SNAPSHOT.jar` — 框架库
- `dbshift-spring-boot-starter/target/dbshift-spring-boot-starter-1.0.0-SNAPSHOT.jar` — Spring Boot 集成

## 配置

`dbshift.yaml`：

```yaml
environments:
  dev:
    url: jdbc:postgresql://localhost:5432/app_dev
    user: app
    password: ${DB_PASSWORD:secret}
    schema: public          # 可选，用于 schema diff 和元数据查询
    # dialect: postgresql   # 可选，不填则从 url 自动识别

  local:
    url: jdbc:sqlite:./data/app.db
    user: ""
    password: ""

  staging:
    url: jdbc:mysql://localhost:3306/app_staging?serverTimezone=UTC
    user: root
    password: ${DB_PASSWORD:root}

migrations:
  dir: ./example/db/migrations

rollback:
  dir: ./example/db/rollback
```

支持 `${ENV_VAR:default}` 环境变量替换。

## 命名规范

- 正向迁移：`V001__description.sql`（版本号连续）
- 回滚脚本：`U001__description.sql`（与 V 编号对应）

## 安全说明

- 已执行脚本被修改后 checksum 校验会失败
- MySQL / SQL Server 等数据库的 DDL 可能自动提交，工具会按方言标注风险
- 回滚需手写 U 脚本，工具不会自动生成
- 生产环境建议：`engine.migrate("prod", null, true, true, "my-app")`（`requireConfirm=true, confirmed=true`）

## 技术栈

- Java 17+
- JSqlParser（SQL 影响分析）
- SnakeYAML（配置）

## License

MIT
