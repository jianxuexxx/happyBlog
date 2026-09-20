# 后端骨架 + 竖切链路（category）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 `backend/` 下搭建可运行的 Spring Boot 3 后端，竖切一条完整链路（HTTP → Controller → Service → MyBatis-Plus → MySQL → Redis），以 `category` 一个模块端到端跑通并被测试覆盖；其余 6 张表建好 DDL / 实体 / Mapper 但暂不写业务。

**架构：** 单一 Spring Boot 应用（端口 18088），包根 `com.blog`，分层 Controller / Service / Mapper / Entity / DTO。采用「显式优先」风格：统一返回用显式 `Result<T>` 类型，缓存键名手写并逐字对齐设计规格，异常显式抛 `BizException`。JWT（HS256）保护 `/api/admin/**`，登录态另存 Redis `blog:admin:token` 以实现真实登出。**所有响应 HTTP 状态码一律 200**，错误只体现在响应体 `code`。

**技术栈：** Spring Boot 3.4.3、Java 21、MyBatis-Plus 3.5.9（`mybatis-plus-spring-boot3-starter`）、MySQL 8.0.32、Redis、java-jwt 4.4.0、springdoc-openapi 2.7.0、Lombok 1.18.36、JUnit5 + Mockito + MockMvc。

**设计依据：** [`docs/superpowers/specs/2026-09-20-backend-skeleton-design.md`](../specs/2026-09-20-backend-skeleton-design.md)

---

## ⚠️ 实现前必读：环境约束

本机 `JAVA_HOME` 指向 **JDK 1.8**（`E:\works\java8\jdk`），Spring Boot 3 需要 17+。**绝对不要修改本机 `JAVA_HOME` 或系统环境变量**，只在每条命令内联覆盖。

JDK 21 位于 `E:\works\jdk21`（Temurin 21.0.12.1 LTS），Maven 位于 `E:\works\apache-maven-3.8.6-bin\apache-maven-3.8.6`（未加入 PATH）。

**本计划中所有 Maven 命令都必须使用如下自包含前缀**（单行内联，不依赖 shell 状态，因为每次 Bash 调用环境不保留）：

```bash
JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn <goal>
```

**依赖下载**：本地仓库 `C:\Users\jianx\.m2\repository`，走用户 `~/.m2/settings.xml` 里的 `alimaven` 镜像（`http://maven.aliyun.com/nexus/content/groups/public/`）。已实测可用，无需改 settings。**不要修改 `~/.m2/settings.xml`**（用户工作项目也在用）。

**验证边界（重要）**：实现者**无法访问 MySQL / Redis 凭证**，因此

- 能验证：编译、Mockito 单测、MockMvc 片段测试（都不需要真实 DB / Redis）
- **不能验证**：启动应用、连库、真实缓存读写 —— 这些由用户跑冒烟脚本（任务 12）
- **不要尝试**启动 `spring-boot:run` 或写任何连接真实数据库的测试。做了必失败

**工作目录**：所有路径以 `D:\projects\myblog\` 为前缀；命令默认在仓库根执行，`cd backend` 的步骤会显式写出。

---

## 文件结构

### 创建（全部相对于 `backend/`）

| 文件 | 职责 |
|------|------|
| `pom.xml` | 依赖与构建配置 |
| `src/main/java/com/blog/MyBlogApplication.java` | 启动类（只含 `@SpringBootApplication`，**不含** `@MapperScan`） |
| `src/main/java/com/blog/common/Result.java` | 统一返回体 `{code, message, data}` |
| `src/main/java/com/blog/common/ResultCode.java` | 错误码枚举（唯一真相源） |
| `src/main/java/com/blog/common/PageResult.java` | 分页返回体（本切片不用，供后续 article 列表） |
| `src/main/java/com/blog/common/BizException.java` | 携带错误码的业务异常 |
| `src/main/java/com/blog/common/GlobalExceptionHandler.java` | 全局异常 → 错误码转译 |
| `src/main/java/com/blog/config/MybatisPlusConfig.java` | 分页拦截器 + `@MapperScan("com.blog.mapper")` |
| `src/main/java/com/blog/config/AuditMetaObjectHandler.java` | 公共字段自动填充 |
| `src/main/java/com/blog/config/RedisConfig.java` | `RedisTemplate<String,Object>` + JSON 序列化 |
| `src/main/java/com/blog/config/WebMvcConfig.java` | 注册 JWT 拦截器 |
| `src/main/java/com/blog/config/OpenApiConfig.java` | springdoc 元信息 |
| `src/main/java/com/blog/security/JwtUtil.java` | JWT 签发 / 校验 |
| `src/main/java/com/blog/security/AdminTokenStore.java` | Redis 中的管理员会话 |
| `src/main/java/com/blog/security/AdminAuthInterceptor.java` | 保护 `/api/admin/**` |
| `src/main/java/com/blog/entity/{Article,Category,Tag,ArticleTag,FriendLink,SiteConfig,Notice}.java` | 7 张表的实体 |
| `src/main/java/com/blog/mapper/{同 7 个}Mapper.java` | MyBatis-Plus Mapper |
| `src/main/java/com/blog/util/CacheUtil.java` | Redis 读写 / 失效封装 |
| `src/main/java/com/blog/dto/{CategorySaveDTO,CategoryVO,LoginDTO,LoginVO}.java` | 传输对象 |
| `src/main/java/com/blog/dto/ValidateGroups.java` | 校验分组标记接口（创建/更新） |
| `src/main/java/com/blog/service/{CategoryService,AdminAuthService}.java` | 服务接口 |
| `src/main/java/com/blog/service/impl/{CategoryServiceImpl,AdminAuthServiceImpl}.java` | 服务实现 |
| `src/main/java/com/blog/controller/{CategoryController,AdminCategoryController,AdminAuthController}.java` | HTTP 层 |
| `src/main/resources/application.yml` | 公共配置 |
| `src/main/resources/application-test.yml` | 测试 profile |
| `src/main/resources/db/schema.sql` | 建库建表（交付用户执行） |
| `config/application-local.yml` | 本地连接串模板（交付用户填写，gitignored） |
| `smoke/category-smoke.sh` | 冒烟脚本（交付用户执行） |
| `src/test/java/com/blog/**` | 见任务 1-10 |

### 修改

| 文件 | 变更 |
|------|------|
| `.gitignore`（仓库根） | 新增 `backend/config/`、`backend/target/`、`*.log` |
| `README.md` | 更新状态行（任务 13） |
| `docs/superpowers/specs/2026-09-17-myblog-design.md` | 更新 §15 序列进度（任务 13） |

---

## 任务 1：工程骨架 + 统一返回与错误码

这是**风险最高**的一步——它一次性验证 Maven + JDK21 + 私有镜像 + 全部依赖版本能否协同工作。此任务通过后，后面 12 个任务才成立。

**文件：**
- 创建：`backend/pom.xml`
- 创建：`backend/src/main/java/com/blog/MyBlogApplication.java`
- 创建：`backend/src/main/java/com/blog/common/Result.java`
- 创建：`backend/src/main/java/com/blog/common/ResultCode.java`
- 创建：`backend/src/main/java/com/blog/common/PageResult.java`
- 创建：`backend/src/main/java/com/blog/common/BizException.java`
- 创建：`backend/src/main/resources/application.yml`
- 创建：`backend/src/main/resources/application-test.yml`
- 测试：`backend/src/test/java/com/blog/common/ResultCodeTest.java`
- 测试：`backend/src/test/java/com/blog/common/ResultTest.java`
- 修改：`.gitignore`

- [ ] **步骤 1：创建 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.3</version>
        <relativePath/>
    </parent>

    <groupId>com.blog</groupId>
    <artifactId>myblog-backend</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>myblog-backend</name>
    <description>我的博客系统后端</description>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mybatis-plus.version>3.5.9</mybatis-plus.version>
        <java-jwt.version>4.4.0</java-jwt.version>
        <springdoc.version>2.7.0</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- MyBatis-Plus：Spring Boot 3 必须用 boot3 starter（普通 starter 是 Spring Boot 2 / javax 体系） -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>com.auth0</groupId>
            <artifactId>java-jwt</artifactId>
            <version>${java-jwt.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **步骤 2：创建 `ResultCode.java`**

错误码是跨前后端的契约，此枚举是唯一真相源。

```java
package com.blog.common;

/**
 * 错误码。取值依据 docs/superpowers/specs/2026-09-20-backend-skeleton-design.md §6。
 * 前端 frontend/src/api/http.ts 依赖 40100 清理登录态，改动需同步前端。
 */
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_INVALID(40001, "参数校验失败"),
    CATEGORY_NAME_EXISTS(40002, "分类名已存在"),
    TOKEN_INVALID(40100, "登录已失效，请重新登录"),
    LOGIN_FAILED(40101, "用户名或密码错误"),
    NOT_FOUND(40400, "资源不存在"),
    SERVER_ERROR(50000, "服务器开小差了"),
    UPLOAD_FAILED(50300, "文件上传失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
```

- [ ] **步骤 3：创建 `Result.java`**

```java
package com.blog.common;

import lombok.Getter;

/**
 * 统一返回体 {code, message, data}，code=0 表示成功。
 * 与 frontend/src/api/http.ts 的 ApiResponse 一一对应。
 */
@Getter
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }
}
```

- [ ] **步骤 4：创建 `PageResult.java`**

```java
package com.blog.common;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页返回体。本切片不产生分页数据（分类量级小），
 * 此类型为后续 article 列表预留，同时在设计规格 §8 中已定义。
 */
@Getter
@Setter
public class PageResult<T> {

    private long total;
    private long page;
    private long pageSize;
    private List<T> list;

    public static <T> PageResult<T> of(long total, long page, long pageSize, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setList(list);
        return result;
    }
}
```

- [ ] **步骤 5：创建 `BizException.java`**

```java
package com.blog.common;

import lombok.Getter;

/** 业务异常，携带错误码。由 GlobalExceptionHandler 统一转译为响应体。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 用于需要覆盖默认文案的场景，例如「分类不存在」比通用的「资源不存在」更具体。 */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }
}
```

- [ ] **步骤 6：创建 `MyBlogApplication.java`**

```java
package com.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyBlogApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyBlogApplication.class, args);
    }
}
```

> **启动类只留 `@SpringBootApplication`，`@MapperScan` 不标在这里。**
> `@MapperScan` 由 `MapperScannerRegistrar` 实现，它是一个 `ImportBeanDefinitionRegistrar`，而 `@WebMvcTest`
> 切片是以启动类为配置类的 —— 注册器随启动类一起进切片，不受切片的类型过滤（`TypeExcludeFilter`）约束。
> 切片里没有 `MybatisPlusAutoConfiguration`，也就没有 `SqlSessionFactory`，于是每个 `MapperFactoryBean`
> 都在 `afterPropertiesSet` 抛 `IllegalArgumentException: Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required`，
> 整个切片上下文加载失败 —— Web 层测试全红，却与 Web 层代码毫无关系。
> 因此 `@MapperScan("com.blog.mapper")` 标在普通 `@Configuration` 的 `MybatisPlusConfig` 上（任务 3 步骤 7）：
> 该类的类型不在 `@WebMvcTest` 的纳入范围（只纳入 `@Controller` / `@ControllerAdvice` / `Filter` /
> `WebMvcConfigurer` / `HandlerInterceptor` 等），切片不会扫它；运行时组件扫描 `com.blog.**` 仍会扫到该类，行为不变。

- [ ] **步骤 7：创建 `application.yml`**

```yaml
server:
  port: 18088

spring:
  application:
    name: myblog-backend
  # 加载本地配置文件 backend/config/application-local.yml（已 gitignore，交付用户填写）。
  # 必须用 spring.config.import —— spring.config.additional-location 一族（含 name/location）
  # 按官方文档必须在环境变量 / 系统属性 / 命令行参数中定义，写在 application.yml 里【静默失效】。
  # import 只能指向文件、不能指向目录；被导入文件的值优先于本文件。
  # 路径相对启动时的工作目录，因此必须在 backend/ 目录下启动；文件不存在时静默跳过。
  config:
    import: optional:file:./config/application-local.yml
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/happyblog?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
  jackson:
    # 注意：不要在这里配 spring.jackson.date-format —— 它只作用于 java.util.Date，
    # 对本项目大量使用的 java.time.LocalDateTime 无效（会输出 ISO-8601）。
    # 将来 article 接口涉及时间字段时，用 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    # 或注册 Jackson2ObjectMapperBuilderCustomizer，别依赖那个属性。
    time-zone: Asia/Shanghai

mybatis-plus:
  configuration:
    # 必须为 false：本项目 DDL 与实体字段统一用驼峰列名（`coverImage`、`categoryName`、`createdAt` …）。
    # 该开关为 true 时，没有显式 @TableField(value=...) 的字段名会被 camelToUnderline 转成下划线
    # 再去找列（cover_image），与 DDL 对不上，BaseMapper 生成的 insert / selectById / updateById /
    # 逻辑删除会全部抛 1054 Unknown column。这是「看着没问题、单测全绿、连真库才炸」的静默陷阱。
    # 回归护栏见 ColumnNamingConventionTest。
    map-underscore-to-camel-case: false
    # log-impl 刻意不在此设置：StdOutImpl 会把每条 SQL 连参数打到标准输出，而本文件随生产包一起上线，
    # 等于让生产环境持续刷日志。本地要看 SQL，在 config/application-local.yml 里自行加
    # （该文件不被 git 跟踪，不会带到生产）：
    #   mybatis-plus.configuration.log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    banner: false
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

blog:
  jwt:
    # 生产必须用环境变量 BLOG_JWT_SECRET 覆盖
    secret: ${BLOG_JWT_SECRET:myblog-dev-secret-override-in-production-0123456789abcdef}
    expire-days: 7
  admin:
    # 单管理员模型（设计规格 §13 YAGNI），生产必须用环境变量覆盖
    username: ${BLOG_ADMIN_USERNAME:admin}
    password: ${BLOG_ADMIN_PASSWORD:admin123}

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **步骤 8：创建 `application-test.yml`**

```yaml
# 测试 profile。
# 现有测试全部以 @WebMvcTest 或纯单元测试运行，不加载完整上下文，
# 因此这里显式排除数据源与 Redis 自动配置，防止将来误加 @SpringBootTest 时
# 因缺少真实连接而失败。
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration
```

- [ ] **步骤 9：编写失败的测试 `ResultCodeTest.java`**

```java
package com.blog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 错误码是跨前后端的契约（前端 http.ts 硬编码依赖 40100）。
 * 此测试锁死取值，防止有人「顺手」改号。
 */
class ResultCodeTest {

    @Test
    @DisplayName("错误码取值与设计规格 §6 一致")
    void codesMatchDesignSpec() {
        assertThat(ResultCode.SUCCESS.getCode()).isZero();
        assertThat(ResultCode.PARAM_INVALID.getCode()).isEqualTo(40001);
        assertThat(ResultCode.CATEGORY_NAME_EXISTS.getCode()).isEqualTo(40002);
        assertThat(ResultCode.TOKEN_INVALID.getCode()).isEqualTo(40100);
        assertThat(ResultCode.LOGIN_FAILED.getCode()).isEqualTo(40101);
        assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(40400);
        assertThat(ResultCode.SERVER_ERROR.getCode()).isEqualTo(50000);
        assertThat(ResultCode.UPLOAD_FAILED.getCode()).isEqualTo(50300);
    }

    @Test
    @DisplayName("成功码的文案为 success，前端以此为成功判据")
    void successMessageIsSuccess() {
        assertThat(ResultCode.SUCCESS.getMessage()).isEqualTo("success");
    }
}
```

- [ ] **步骤 10：运行测试，验证工具链与依赖解析**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=ResultCodeTest
```
预期：**首次运行会下载全部依赖（可能数分钟）**，随后 PASS（2 个用例）。

本步骤**不是** TDD 的「先看它失败」环节 —— 错误码取值由前端 `http.ts` 这份外部契约规定，测试在这里的作用是**锁死规格**，而非驱动新行为，所以它一开始就会通过。这一步真正要验证的是**工具链**：Maven + JDK21 + 私有镜像 + 全部依赖版本能否协同工作。这是本任务风险最高的一环，因此在写更多代码之前先跑通它。

> 若报 `Blocked mirror for repositories` —— 说明 Maven 走了 http 拦截分支。检查是否有人改了 `~/.m2/settings.xml`；**不要自己改 settings**，把错误原文回报给用户。
>
> 若报 `release version 21 not supported` 或类似 —— `JAVA_HOME` 没生效，检查上面命令的内联前缀是否漏了。

- [ ] **步骤 11：编写测试 `ResultTest.java`**

```java
package com.blog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    @DisplayName("ok 带数据：code 为 0，message 为 success，data 为传入值")
    void okWithData() {
        Result<String> result = Result.ok("hello");

        assertThat(result.getCode()).isZero();
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getData()).isEqualTo("hello");
    }

    @Test
    @DisplayName("ok 不带数据：data 为 null")
    void okWithoutData() {
        Result<Void> result = Result.ok();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("fail 用枚举：携带枚举的 code 与 message，data 为 null")
    void failWithResultCode() {
        Result<Void> result = Result.fail(ResultCode.CATEGORY_NAME_EXISTS);

        assertThat(result.getCode()).isEqualTo(40002);
        assertThat(result.getMessage()).isEqualTo("分类名已存在");
        assertThat(result.getData()).isNull();
    }

    @Test
    @DisplayName("fail 用自定义文案：code 取自枚举，message 用传入值")
    void failWithCustomMessage() {
        Result<Void> result = Result.fail(ResultCode.NOT_FOUND.getCode(), "分类不存在");

        assertThat(result.getCode()).isEqualTo(40400);
        assertThat(result.getMessage()).isEqualTo("分类不存在");
    }
}
```

- [ ] **步骤 12：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest='ResultCodeTest,ResultTest'
```
预期：PASS，2 个测试类共 6 个用例通过。

- [ ] **步骤 13：更新 `.gitignore`**

在仓库根 `.gitignore` 末尾追加：

```gitignore

# 后端
backend/target/
backend/config/
*.log
```

- [ ] **步骤 14：Commit**

```bash
cd D:/projects/myblog && git add .gitignore backend/pom.xml backend/src && git commit -m "$(cat <<'EOF'
feat: 后端工程骨架与统一返回契约

Spring Boot 3.4.3 + Java 21 + MyBatis-Plus 3.5.9 工程搭建，
Result 统一返回体与 ResultCode 错误码枚举（前端 http.ts 依赖 40100）。
本地配置目录 backend/config/ 加入 gitignore。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 2：全局异常处理

**文件：**
- 创建：`backend/src/main/java/com/blog/common/GlobalExceptionHandler.java`
- 测试：`backend/src/test/java/com/blog/common/GlobalExceptionHandlerTest.java`

- [ ] **步骤 1：编写失败的测试 `GlobalExceptionHandlerTest.java`**

```java
package com.blog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("业务异常：原样转译 code 与 message")
    void handlesBizException() {
        Result<Void> result = handler.handleBiz(new BizException(ResultCode.CATEGORY_NAME_EXISTS));

        assertThat(result.getCode()).isEqualTo(40002);
        assertThat(result.getMessage()).isEqualTo("分类名已存在");
    }

    @Test
    @DisplayName("业务异常可覆盖文案")
    void handlesBizExceptionWithCustomMessage() {
        Result<Void> result = handler.handleBiz(new BizException(ResultCode.NOT_FOUND, "分类不存在"));

        assertThat(result.getCode()).isEqualTo(40400);
        assertThat(result.getMessage()).isEqualTo("分类不存在");
    }

    @Test
    @DisplayName("参数校验失败：code 为 40001，message 含字段名")
    void handlesValidationException() throws Exception {
        LoginDTOForTest target = new LoginDTOForTest();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "dto");
        bindingResult.addError(new FieldError("dto", "username", "不能为空"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        Result<Void> result = handler.handleValidation(ex);

        assertThat(result.getCode()).isEqualTo(40001);
        assertThat(result.getMessage()).contains("username").contains("不能为空");
    }

    @Test
    @DisplayName("未知异常：code 为 50000，且响应体不泄漏堆栈信息")
    void handlesUnknownExceptionWithoutLeakingStackTrace() {
        RuntimeException ex = new RuntimeException("jdbc connection refused: password=secret");

        Result<Void> result = handler.handleUnknown(ex);

        assertThat(result.getCode()).isEqualTo(50000);
        assertThat(result.getMessage()).isEqualTo("服务器开小差了");
        assertThat(result.getMessage()).doesNotContain("jdbc").doesNotContain("password");
        assertThat(result.getData()).isNull();
    }

    /** MethodArgumentNotValidException 需要一个非 null 的目标对象承载 bindingResult。 */
    static class LoginDTOForTest {
        private String username;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=GlobalExceptionHandlerTest
```
预期：编译失败 —— `GlobalExceptionHandler` 不存在。

- [ ] **步骤 3：编写 `GlobalExceptionHandler.java`**

```java
package com.blog.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 * 关键约束：不设置非 200 的 HTTP 状态码 —— 前端 axios 拦截器只在成功回调里
 * 读取响应体 code（frontend/src/api/http.ts:29-39），返回 4xx/5xx 会让前端
 * 拿不到业务错误码。错误只体现在响应体 code 字段。
 * 另一个关键约束：响应体绝不包含堆栈或 SQL 片段，完整堆栈只进日志。
 *
 * 分层意图：客户端错误先被精确 handler 拦下 → 4xxxx + WARN 且不打堆栈；
 * 只有真正的未预期异常才落到最后的 catch-all → 50000 + ERROR + 全堆栈。
 * 若不加这层区分，404/405/畸形请求体都会被误报成「服务器开小差了」，
 * 并在日志里刷 ERROR 堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常 code={} message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? ResultCode.PARAM_INVALID.getMessage()
                : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), message);
    }

    /** 路径不存在。Spring Boot 3.2+ 对未匹配的请求抛此异常（含 favicon、扫描器探测）。属客户端错误。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("路径不存在: {}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /** 请求方法不匹配，例如对只支持 GET 的路径发 POST。属客户端错误。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), "请求方法不支持");
    }

    /** 请求体畸形或无法解析。属客户端错误，不该报「服务器开小差了」。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        // 只记简短原因，不打全堆栈 —— 畸形请求体是常见噪声，不是服务端故障
        log.warn("请求体无法解析: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_INVALID.getCode(), "请求体格式错误");
    }

    /** 路径变量或查询参数类型不匹配，例如 /api/admin/category/abc 期望 Long。属客户端错误。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={} value={}", e.getName(), e.getValue());
        return Result.fail(ResultCode.PARAM_INVALID.getCode(),
                "参数 " + e.getName() + " 类型不正确");
    }

    /** 真正的未预期异常。客户端错误应在此之前的精确 handler 里被拦下。 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        // 完整堆栈只进日志，响应体只给固定友好文案
        log.error("未预期异常", e);
        return Result.fail(ResultCode.SERVER_ERROR);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=GlobalExceptionHandlerTest
```
预期：PASS。原 4 个用例 + 4 个新 handler 各一个用例 + 1 个 catch-all 回归护栏用例。

- [ ] **步骤 5：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "$(cat <<'EOF'
feat: 全局异常处理，错误只体现在响应体 code

BizException 原样转译；参数校验失败拼字段名；未知异常固定文案，
完整堆栈只进日志不泄漏到响应。HTTP 状态码不设非 200，因为前端
axios 拦截器只在成功回调里读取业务 code。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 3：数据层基建（实体 + Mapper + 自动填充 + 逻辑删除 + 分页）

**文件：**
- 创建：`backend/src/main/java/com/blog/entity/{Article,Category,Tag,ArticleTag,FriendLink,SiteConfig,Notice}.java`
- 创建：`backend/src/main/java/com/blog/mapper/{Article,Category,Tag,ArticleTag,FriendLink,SiteConfig,Notice}Mapper.java`
- 创建：`backend/src/main/java/com/blog/config/MybatisPlusConfig.java`
- 创建：`backend/src/main/java/com/blog/config/AuditMetaObjectHandler.java`
- 测试：`backend/src/test/java/com/blog/config/AuditMetaObjectHandlerTest.java`

- [ ] **步骤 1：编写失败的测试 `AuditMetaObjectHandlerTest.java`**

自动填充是本切片最容易静默失效的一环（漏标 `@TableField(fill=...)` 不报错，只是字段永远为 null）。用 `SystemMetaObject` 直接驱动填充逻辑，**不需要数据库**。

```java
package com.blog.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.blog.entity.Category;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditMetaObjectHandlerTest {

    private final AuditMetaObjectHandler handler = new AuditMetaObjectHandler();

    @Test
    @DisplayName("插入时填充 createdAt、updatedAt 与 deleted=0")
    void insertFillFillsAuditFieldsAndDeletedFlag() {
        Category entity = new Category();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getDeleted()).isZero();
    }

    @Test
    @DisplayName("插入时不覆盖已显式设置的 deleted")
    void insertFillDoesNotOverrideExplicitDeleted() {
        Category entity = new Category();
        entity.setDeleted(1);
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.insertFill(metaObject);

        assertThat(entity.getDeleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("更新时只填 updatedAt，不动 createdAt 与 deleted")
    void updateFillOnlyFillsUpdatedAt() {
        Category entity = new Category();
        MetaObject metaObject = SystemMetaObject.forObject(entity);

        handler.updateFill(metaObject);

        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getDeleted()).isNull();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AuditMetaObjectHandlerTest
```
预期：编译失败 —— `Category` 与 `AuditMetaObjectHandler` 都不存在。

- [ ] **步骤 3：创建 7 个实体**

`backend/src/main/java/com/blog/entity/Article.java`：

```java
package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("article")
public class Article {

    @TableId(value = "articleId", type = IdType.AUTO)
    private Long articleId;

    private String title;
    private String summary;
    private String content;
    private String coverImage;
    private Long categoryId;

    /** 0草稿 / 1公开 / 2私密 */
    private Integer status;
    private Integer isTop;
    private Integer isRecommended;
    private Integer viewCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

`backend/src/main/java/com/blog/entity/Category.java`：

```java
package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("category")
public class Category {

    @TableId(value = "categoryId", type = IdType.AUTO)
    private Long categoryId;

    private String categoryName;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

`backend/src/main/java/com/blog/entity/Tag.java`：

```java
package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("tag")
public class Tag {

    @TableId(value = "tagId", type = IdType.AUTO)
    private Long tagId;

    private String tagName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

`backend/src/main/java/com/blog/entity/ArticleTag.java`：

```java
package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 文章-标签多对多关联。删除一律逻辑删除（设计规格 §4 全局约束）。 */
@Data
@TableName("articleTag")
public class ArticleTag {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long articleId;
    private Long tagId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

`backend/src/main/java/com/blog/entity/FriendLink.java`：

```java
package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("friendLink")
public class FriendLink {

    @TableId(value = "friendLinkId", type = IdType.AUTO)
    private Long friendLinkId;

    private String name;
    private String url;
    private String avatar;
    private String description;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

`backend/src/main/java/com/blog/entity/SiteConfig.java`：

```java
package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 站点配置键值表。主键为业务键 configKey，无自增列，故 IdType 为 INPUT。 */
@Data
@TableName("siteConfig")
public class SiteConfig {

    @TableId(value = "configKey", type = IdType.INPUT)
    private String configKey;

    private String configValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

`backend/src/main/java/com/blog/entity/Notice.java`：

```java
package com.blog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("notice")
public class Notice {

    @TableId(value = "noticeId", type = IdType.AUTO)
    private Long noticeId;

    private String title;
    private String content;

    /** 生效时间起 */
    private LocalDateTime startsAt;

    /** 生效时间止，可空表示长期有效 */
    private LocalDateTime endsAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
```

- [ ] **步骤 4：创建 `AuditMetaObjectHandler.java`**

```java
package com.blog.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * 公共审计字段自动填充（设计规格 §8）。
 * MyBatis-Plus 会自动侦测容器中的 MetaObjectHandler bean，无需在 MybatisPlusConfig 中注册。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);

        // deleted 走非严格填充：显式设置过就不覆盖（例如数据修复场景）
        if (getFieldValByName("deleted", metaObject) == null) {
            setFieldValByName("deleted", 0, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AuditMetaObjectHandlerTest
```
预期：PASS，3 个用例通过。

- [ ] **步骤 6：创建 7 个 Mapper**

`backend/src/main/java/com/blog/mapper/CategoryMapper.java`（本切片唯一有自定义 SQL 的 Mapper）：

```java
package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.dto.CategoryVO;
import com.blog.entity.Category;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 分类列表含文章数。逻辑删除条件必须显式写进 JOIN 条件 ——
     * @TableLogic 只对 MyBatis-Plus 生成的 SQL 生效，手写 SQL 不受其保护。
     * 若把 a.deleted = 0 写进 WHERE，会把「分类下文章全被删」的分类也过滤掉。
     */
    @Select("""
            SELECT c.categoryId, c.categoryName, c.sortOrder,
                   COUNT(a.articleId) AS articleCount
            FROM category c
            LEFT JOIN article a
                   ON a.categoryId = c.categoryId
                  AND a.deleted = 0
            WHERE c.deleted = 0
            GROUP BY c.categoryId, c.categoryName, c.sortOrder
            ORDER BY c.sortOrder ASC, c.categoryId ASC
            """)
    List<CategoryVO> selectCategoryWithArticleCount();
}
```

> **注意**：此 Mapper 引用了 `com.blog.dto.CategoryVO`（任务 7 创建）。为保持任务可独立编译，**在本步骤先创建 `CategoryVO.java`**（内容见任务 7 步骤 3），任务 7 不再重复创建。

`backend/src/main/java/com/blog/mapper/ArticleMapper.java`：

```java
package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Article;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 分类被删除时，把该分类下未删文章的 categoryId 置空（设计规格 §4 关系约束）。
     * 手写 UPDATE 不受 @TableLogic 与自动填充保护，故 deleted 条件与 updatedAt 都显式写出。
     */
    @Update("""
            UPDATE article
               SET categoryId = NULL,
                   updatedAt = NOW()
             WHERE categoryId = #{categoryId}
               AND deleted = 0
            """)
    int clearCategoryId(@Param("categoryId") Long categoryId);
}
```

其余 5 个 Mapper 结构相同，仅泛型不同：

`backend/src/main/java/com/blog/mapper/TagMapper.java`：
```java
package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Tag;

public interface TagMapper extends BaseMapper<Tag> {
}
```

`backend/src/main/java/com/blog/mapper/ArticleTagMapper.java`：
```java
package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.ArticleTag;

public interface ArticleTagMapper extends BaseMapper<ArticleTag> {
}
```

`backend/src/main/java/com/blog/mapper/FriendLinkMapper.java`：
```java
package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.FriendLink;

public interface FriendLinkMapper extends BaseMapper<FriendLink> {
}
```

`backend/src/main/java/com/blog/mapper/SiteConfigMapper.java`：
```java
package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.SiteConfig;

public interface SiteConfigMapper extends BaseMapper<SiteConfig> {
}
```

`backend/src/main/java/com/blog/mapper/NoticeMapper.java`：
```java
package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Notice;

public interface NoticeMapper extends BaseMapper<Notice> {
}
```

- [ ] **步骤 7：创建 `MybatisPlusConfig.java`**

```java
package com.blog.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 * 本切片的 category/list 不分页（分类量级小），分页插件在此就位供后续 article 列表直接使用。
 * MetaObjectHandler 由容器自动侦测，不在此注册。
 *
 * 【@MapperScan 为何在本类而不是 MyBlogApplication —— 勿挪回去】
 * 原因见任务 1 步骤 6 的说明：标在 @SpringBootApplication 类上会被每个 @WebMvcTest 切片
 * 连同该类一起加载，而切片里没有 SqlSessionFactory，Mapper 注册即失败，Web 层测试全红。
 */
@Configuration
@MapperScan("com.blog.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **步骤 8：全量编译验证**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test
```
预期：PASS。此时已有 ResultCodeTest、ResultTest、GlobalExceptionHandlerTest、AuditMetaObjectHandlerTest 共 4 个测试类通过。

- [ ] **步骤 9：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "$(cat <<'EOF'
feat: 数据层基建——7 表实体/Mapper、自动填充、逻辑删除、分页插件

实体以驼峰字段名直接对应 DDL 驼峰列名，@TableLogic 实现逻辑删除，
MetaObjectHandler 填充 createdAt/updatedAt/deleted。
CategoryMapper 手写含文章数的查询，逻辑删除条件显式写进 JOIN
（手写 SQL 不受 @TableLogic 保护）。ArticleMapper 提供清空分类外键的方法。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 4：Redis 缓存基建

**文件：**
- 创建：`backend/src/main/java/com/blog/config/RedisConfig.java`
- 创建：`backend/src/main/java/com/blog/util/CacheUtil.java`
- 测试：`backend/src/test/java/com/blog/util/CacheUtilTest.java`

- [ ] **步骤 1：编写失败的测试 `CacheUtilTest.java`**

用 Mockito 打桩 `RedisTemplate`，**不需要真实 Redis**。

```java
package com.blog.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.blog.dto.CategoryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CacheUtilTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheUtil cacheUtil;

    @BeforeEach
    void setUp() {
        cacheUtil = new CacheUtil(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("set：写入键值并带上 TTL")
    void setWritesValueWithTtl() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        cacheUtil.set("blog:category:list", List.of(), Duration.ofMinutes(5));

        verify(valueOperations).set("blog:category:list", List.of(), Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("get：命中时反序列化为目标类型")
    void getConvertsHit() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("categoryId", 1);
        raw.put("categoryName", "技术");
        raw.put("sortOrder", 0);
        raw.put("articleCount", 3);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(raw);

        CategoryVO vo = cacheUtil.get("blog:category:list", CategoryVO.class);

        assertThat(vo).isNotNull();
        assertThat(vo.getCategoryName()).isEqualTo("技术");
        assertThat(vo.getArticleCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("get：未命中返回 null")
    void getReturnsNullOnMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(null);

        assertThat(cacheUtil.get("blog:category:list", CategoryVO.class)).isNull();
    }

    @Test
    @DisplayName("getList：命中时反序列化为元素列表")
    void getListConvertsElementType() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("categoryId", 1);
        first.put("categoryName", "技术");
        first.put("sortOrder", 0);
        first.put("articleCount", 3);
        List<Object> raw = new ArrayList<>();
        raw.add(first);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(raw);

        List<CategoryVO> list = cacheUtil.getList("blog:category:list", CategoryVO.class);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCategoryName()).isEqualTo("技术");
        assertThat(list.get(0).getArticleCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("getList：未命中返回 null（而非空列表，以便调用方区分「无缓存」与「缓存了空集」）")
    void getListReturnsNullOnMiss() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("blog:category:list")).willReturn(null);

        assertThat(cacheUtil.getList("blog:category:list", CategoryVO.class)).isNull();
    }

    @Test
    @DisplayName("delete：删除键")
    void deleteRemovesKey() {
        cacheUtil.delete("blog:category:list");

        verify(redisTemplate).delete("blog:category:list");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CacheUtilTest
```
预期：编译失败 —— `CacheUtil` 不存在，且 `CategoryVO` 应已由任务 3 步骤 6 创建（若报 `CategoryVO` 找不到，回去补任务 3 步骤 6 的 `CategoryVO.java`）。

- [ ] **步骤 3：创建 `RedisConfig.java`**

```java
package com.blog.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置。
 * 键用 String（可读，便于 redis-cli 直接查设计规格 §9 定义的键名），
 * 值用 JSON。刻意不开启 Jackson 多态类型信息（default typing）：
 * 存进去的是纯净 JSON，读出来由 CacheUtil 用 convertValue 还原成调用方要的类型，
 * 既避免启用 default typing 的安全面，也避免类型信息把 redis 里的值弄脏。
 * 复用 Spring 容器里的 ObjectMapper，保证与 CacheUtil 的转换配置一致。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

- [ ] **步骤 4：创建 `CacheUtil.java`**

```java
package com.blog.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 读写封装（设计规格 §8「显式优先」）。
 * 键名由调用方显式传入并逐字对齐设计规格 §9，不做任何前缀拼接，
 * 这样 redis-cli 里看到的键名与规格文档一致，排查时可直接对照。
 */
@Component
public class CacheUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheUtil(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    /** 未命中返回 null。 */
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(value, type);
    }

    /** 未命中返回 null（而非空列表），使调用方能区分「无缓存」与「缓存了空集」。 */
    public <T> List<T> getList(String key, Class<T> elementType) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return objectMapper.convertValue(
                value,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CacheUtilTest
```
预期：PASS，6 个用例通过。

- [ ] **步骤 6：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "$(cat <<'EOF'
feat: Redis 缓存基建——RedisConfig 与 CacheUtil

键用 String 序列化以便 redis-cli 直接对照设计规格键名；值用纯 JSON，
不开 Jackson default typing，读取时由 CacheUtil convertValue 还原目标类型。
键名由调用方显式传入，不做前缀拼接。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 5：JWT 基建

**文件：**
- 创建：`backend/src/main/java/com/blog/security/JwtUtil.java`
- 创建：`backend/src/main/java/com/blog/security/AdminTokenStore.java`
- 测试：`backend/src/test/java/com/blog/security/JwtUtilTest.java`
- 测试：`backend/src/test/java/com/blog/security/AdminTokenStoreTest.java`

- [ ] **步骤 1：编写失败的测试 `JwtUtilTest.java`**

```java
package com.blog.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789abcdef";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 7);

    @Test
    @DisplayName("签发的 token 校验通过并返回用户名")
    void generatedTokenVerifies() {
        String token = jwtUtil.generate("admin");

        assertThat(jwtUtil.verify(token)).isEqualTo("admin");
    }

    @Test
    @DisplayName("被篡改的 token 校验失败返回 null")
    void tamperedTokenFailsVerification() {
        String token = jwtUtil.generate("admin");
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        assertThat(jwtUtil.verify(tampered)).isNull();
    }

    @Test
    @DisplayName("用其他密钥签发的 token 校验失败")
    void tokenSignedWithOtherSecretFails() {
        JwtUtil other = new JwtUtil("another-secret-0123456789abcdef0123456789abcdef", 7);
        String foreign = other.generate("admin");

        assertThat(jwtUtil.verify(foreign)).isNull();
    }

    @Test
    @DisplayName("已过期的 token 校验失败")
    void expiredTokenFailsVerification() {
        // expireDays 为负 → 过期时间落在过去
        JwtUtil expiredIssuer = new JwtUtil(SECRET, -1);
        String expired = expiredIssuer.generate("admin");

        assertThat(jwtUtil.verify(expired)).isNull();
    }

    @Test
    @DisplayName("非 JWT 格式的字符串校验失败返回 null 而非抛异常")
    void garbageTokenReturnsNullInsteadOfThrowing() {
        assertThat(jwtUtil.verify("not-a-jwt")).isNull();
        assertThat(jwtUtil.verify("")).isNull();
    }

    @Test
    @DisplayName("getTtl 返回配置的天数")
    void getTtlReturnsConfiguredDuration() {
        assertThat(jwtUtil.getTtl()).isEqualTo(Duration.ofDays(7));

        assertThat(new JwtUtil(SECRET, 3).getTtl()).isEqualTo(Duration.ofDays(3));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=JwtUtilTest
```
预期：编译失败 —— `JwtUtil` 不存在。

- [ ] **步骤 3：创建 `JwtUtil.java`**

```java
package com.blog.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 签发与校验（HS256）。校验失败一律返回 null，不抛异常 —— 调用方只需判空。 */
@Component
public class JwtUtil {

    private final String secret;
    private final int expireDays;

    public JwtUtil(@Value("${blog.jwt.secret}") String secret,
                   @Value("${blog.jwt.expire-days:7}") int expireDays) {
        this.secret = secret;
        this.expireDays = expireDays;
    }

    public String generate(String username) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(username)
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(now)
                .withExpiresAt(now.plus(expireDays, ChronoUnit.DAYS))
                .sign(Algorithm.HMAC256(secret));
    }

    /** 校验签名与过期时间。通过返回用户名，失败返回 null。 */
    public String verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
            return verifier.verify(token).getSubject();
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    public Duration getTtl() {
        return Duration.ofDays(expireDays);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=JwtUtilTest
```
预期：PASS，6 个用例通过。

- [ ] **步骤 5：编写失败的测试 `AdminTokenStoreTest.java`**

```java
package com.blog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.blog.util.CacheUtil;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminTokenStoreTest {

    @Mock
    private CacheUtil cacheUtil;

    @Mock
    private JwtUtil jwtUtil;

    private AdminTokenStore tokenStore;

    @BeforeEach
    void setUp() {
        tokenStore = new AdminTokenStore(cacheUtil, jwtUtil);
    }

    @Test
    @DisplayName("save：以设计规格 §9 定义的键写入，TTL 取自 token 有效期")
    void saveUsesSpecKeyAndTokenTtl() {
        given(jwtUtil.getTtl()).willReturn(Duration.ofDays(7));

        tokenStore.save("a-token");

        verify(cacheUtil).set("blog:admin:token", "a-token", Duration.ofDays(7));
    }

    @Test
    @DisplayName("matches：存储值一致返回 true")
    void matchesReturnsTrueForSameToken() {
        given(cacheUtil.get("blog:admin:token", String.class)).willReturn("a-token");

        assertThat(tokenStore.matches("a-token")).isTrue();
    }

    @Test
    @DisplayName("matches：存储值不一致返回 false（旧 token 被新登录顶掉）")
    void matchesReturnsFalseForDifferentToken() {
        given(cacheUtil.get("blog:admin:token", String.class)).willReturn("newer-token");

        assertThat(tokenStore.matches("a-token")).isFalse();
    }

    @Test
    @DisplayName("matches：键不存在返回 false（已登出或已过期）")
    void matchesReturnsFalseWhenAbsent() {
        given(cacheUtil.get("blog:admin:token", String.class)).willReturn(null);

        assertThat(tokenStore.matches("a-token")).isFalse();
    }

    @Test
    @DisplayName("clear：删除会话键，使登出立即生效")
    void clearDeletesSessionKey() {
        tokenStore.clear();

        verify(cacheUtil).delete("blog:admin:token");
    }
}
```

- [ ] **步骤 6：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AdminTokenStoreTest
```
预期：编译失败 —— `AdminTokenStore` 不存在。

- [ ] **步骤 7：创建 `AdminTokenStore.java`**

```java
package com.blog.security;

import com.blog.util.CacheUtil;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 管理员会话存储（设计规格 §9 的 blog:admin:token 键）。
 * 单管理员模型，单值键 —— 再次登录会顶掉上一个 token。
 * 之所以在 JWT 之外另存一份：JWT 一旦签发就无法撤回，
 * 存一份才能让「登出」立即生效（校验签名 + 校验仍在存储中）。
 */
@Component
public class AdminTokenStore {

    /** 设计规格 §9 定义的键，勿改。 */
    public static final String KEY = "blog:admin:token";

    private final CacheUtil cacheUtil;
    private final JwtUtil jwtUtil;

    public AdminTokenStore(CacheUtil cacheUtil, JwtUtil jwtUtil) {
        this.cacheUtil = cacheUtil;
        this.jwtUtil = jwtUtil;
    }

    public void save(String token) {
        cacheUtil.set(KEY, token, jwtUtil.getTtl());
    }

    public boolean matches(String token) {
        return Objects.equals(token, cacheUtil.get(KEY, String.class));
    }

    public void clear() {
        cacheUtil.delete(KEY);
    }
}
```

- [ ] **步骤 8：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AdminTokenStoreTest
```
预期：PASS，5 个用例通过。

- [ ] **步骤 9：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "$(cat <<'EOF'
feat: JWT 基建——JwtUtil 与 AdminTokenStore

HS256 签发/校验，校验失败返回 null 不抛异常。会话另存 Redis
blog:admin:token 以支持登出立即生效（JWT 签发后无法撤回）。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 6：鉴权拦截器与 WebMvc 注册

**文件：**
- 创建：`backend/src/main/java/com/blog/security/AdminAuthInterceptor.java`
- 创建：`backend/src/main/java/com/blog/config/WebMvcConfig.java`
- 测试：`backend/src/test/java/com/blog/security/AdminAuthInterceptorTest.java`

- [ ] **步骤 1：编写失败的测试 `AdminAuthInterceptorTest.java`**

注意其中一条测试专门锁死「拒绝时 HTTP 状态仍为 200」，这是本设计最容易被人「顺手修正」成 401 的地方。

```java
package com.blog.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AdminAuthInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AdminTokenStore tokenStore;

    private AdminAuthInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(jwtUtil, tokenStore, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("缺少 Authorization 头：拒绝，响应体 code 为 40100")
    void rejectsWhenHeaderMissing() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("Authorization 头前缀不是 Bearer：拒绝")
    void rejectsWhenPrefixWrong() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("token 签名或过期时间无效：拒绝")
    void rejectsWhenTokenInvalid() throws Exception {
        request.addHeader("Authorization", "Bearer bad-token");
        given(jwtUtil.verify("bad-token")).willReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("token 有效但存储中不存在（已登出）：拒绝")
    void rejectsWhenTokenRevoked() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(tokenStore.matches("good-token")).willReturn(false);

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":40100");
    }

    @Test
    @DisplayName("token 有效且存储中存在：放行")
    void allowsWhenTokenValidAndStored() throws Exception {
        request.addHeader("Authorization", "Bearer good-token");
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(tokenStore.matches("good-token")).willReturn(true);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("【契约】拒绝时 HTTP 状态仍为 200，否则前端 axios 走错误分支读不到 code")
    void rejectsWithHttp200() throws Exception {
        interceptor.preHandle(request, response, new Object());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).contains("application/json");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AdminAuthInterceptorTest
```
预期：编译失败 —— `AdminAuthInterceptor` 不存在。

- [ ] **步骤 3：创建 `AdminAuthInterceptor.java`**

```java
package com.blog.security;

import com.blog.common.Result;
import com.blog.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 保护 /api/admin/**（登录接口在 WebMvcConfig 中排除）。
 *
 * 【关键契约】拒绝时返回 HTTP 200 + 响应体 code=40100，不返回 HTTP 401。
 * 原因：frontend/src/api/http.ts:29-39 把 40100 的处理放在 axios 响应拦截器的
 * 成功回调里，axios 默认只把 2xx 视为成功。若这里返回 401，前端会走错误分支，
 * 清理 blog-admin-token 的逻辑永不执行，表现为「token 过期后卡在管理页」。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AdminTokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(JwtUtil jwtUtil,
                                AdminTokenStore tokenStore,
                                ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return reject(response);
        }

        String token = header.substring(PREFIX.length()).trim();
        if (jwtUtil.verify(token) == null || !tokenStore.matches(token)) {
            return reject(response);
        }
        return true;
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.TOKEN_INVALID)));
        return false;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AdminAuthInterceptorTest
```
预期：PASS，6 个用例通过。

- [ ] **步骤 5：创建 `WebMvcConfig.java`**

```java
package com.blog.config;

import com.blog.security.AdminAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebMvcConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                // 登录接口本身不能要求携带 token
                .excludePathPatterns("/api/admin/login");
    }
}
```

- [ ] **步骤 6：全量测试验证**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test
```
预期：PASS。此时已有 8 个测试类通过：ResultCodeTest、ResultTest、GlobalExceptionHandlerTest、AuditMetaObjectHandlerTest、CacheUtilTest、JwtUtilTest、AdminTokenStoreTest、AdminAuthInterceptorTest。

- [ ] **步骤 7：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "$(cat <<'EOF'
feat: JWT 拦截器保护管理端接口

拦截 /api/admin/**（排除登录）。拒绝时返回 HTTP 200 + code 40100 而非
HTTP 401——前端 axios 拦截器只在成功回调里处理 40100，返回 401 会让
前端读不到该码、清理登录态的逻辑永不执行。已用测试锁死该契约。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 7：category 读链路（VO + 自定义查询 + Service.list + 缓存）

**文件：**
- 创建：`backend/src/main/java/com/blog/dto/CategoryVO.java`（若任务 3 步骤 6 已建则跳过）
- 创建：`backend/src/main/java/com/blog/service/CategoryService.java`
- 创建：`backend/src/main/java/com/blog/service/impl/CategoryServiceImpl.java`
- 测试：`backend/src/test/java/com/blog/service/impl/CategoryServiceImplTest.java`

- [ ] **步骤 1：编写失败的测试（本任务部分）**

本任务的测试与任务 8 同属一个测试类。**先只写读链路的用例**，写链路的用例留到任务 8 追加。

`backend/src/test/java/com/blog/service/impl/CategoryServiceImplTest.java`：

```java
package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.blog.dto.CategoryVO;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.util.CacheUtil;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private CacheUtil cacheUtil;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryMapper, articleMapper, cacheUtil);
    }

    private CategoryVO vo(long id, String name, int sortOrder, int articleCount) {
        CategoryVO vo = new CategoryVO();
        vo.setCategoryId(id);
        vo.setCategoryName(name);
        vo.setSortOrder(sortOrder);
        vo.setArticleCount(articleCount);
        return vo;
    }

    @Test
    @DisplayName("列表：缓存命中时直接返回，不查库")
    void listReturnsFromCacheWithoutHittingDb() {
        List<CategoryVO> cached = List.of(vo(1L, "技术", 0, 3));
        given(cacheUtil.getList("blog:category:list", CategoryVO.class)).willReturn(cached);

        List<CategoryVO> result = service.list();

        assertThat(result).isEqualTo(cached);
        verify(categoryMapper, never()).selectCategoryWithArticleCount();
    }

    @Test
    @DisplayName("列表：缓存未命中时查库并以 5 分钟 TTL 写入设计规格定义的键")
    void listLoadsFromDbAndCachesOnMiss() {
        List<CategoryVO> fromDb = List.of(vo(1L, "技术", 0, 3), vo(2L, "生活", 1, 0));
        given(cacheUtil.getList("blog:category:list", CategoryVO.class)).willReturn(null);
        given(categoryMapper.selectCategoryWithArticleCount()).willReturn(fromDb);

        List<CategoryVO> result = service.list();

        assertThat(result).isEqualTo(fromDb);
        verify(cacheUtil).set("blog:category:list", fromDb, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("列表：库中无数据时也写缓存（空集也要缓存，避免每次穿透）")
    void listCachesEmptyResult() {
        List<CategoryVO> empty = List.of();
        given(cacheUtil.getList("blog:category:list", CategoryVO.class)).willReturn(null);
        given(categoryMapper.selectCategoryWithArticleCount()).willReturn(empty);

        List<CategoryVO> result = service.list();

        assertThat(result).isEmpty();
        verify(cacheUtil).set("blog:category:list", empty, Duration.ofMinutes(5));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CategoryServiceImplTest
```
预期：编译失败 —— `CategoryServiceImpl` 不存在。

- [ ] **步骤 3：创建 `CategoryVO.java`（若任务 3 步骤 6 已建，跳过本步骤）**

```java
package com.blog.dto;

import lombok.Data;

/** 分类列表项。articleCount 由 CategoryMapper 的 JOIN 查询算出。 */
@Data
public class CategoryVO {

    private Long categoryId;
    private String categoryName;
    private Integer sortOrder;
    private Integer articleCount;
}
```

- [ ] **步骤 4：创建 `CategoryService.java`**

```java
package com.blog.service;

import com.blog.dto.CategorySaveDTO;
import com.blog.dto.CategoryVO;
import java.util.List;

public interface CategoryService {

    /** 分类列表含文章数，走 Redis 缓存。 */
    List<CategoryVO> list();

    /** 新增，返回新建的分类 ID。 */
    Long create(CategorySaveDTO dto);

    void update(CategorySaveDTO dto);

    /** 逻辑删除分类，并把该分类下文章的 categoryId 置空。 */
    void delete(Long categoryId);
}
```

> **注意**：`CategoryService` 引用了 `CategorySaveDTO`（任务 8 创建）。为保持本任务可独立编译，**同时创建 `CategorySaveDTO.java`**（内容见任务 8 步骤 3），任务 8 不再重复创建。

- [ ] **步骤 5：创建 `CategoryServiceImpl.java`（本任务只实现 `list`，其余方法留待任务 8）**

为保持类可编译，此步骤先把 `create` / `update` / `delete` 写成 `throw new UnsupportedOperationException()`，**任务 8 会替换为真实实现**。

```java
package com.blog.service.impl;

import com.blog.common.BizException;
import com.blog.common.ResultCode;
import com.blog.dto.CategorySaveDTO;
import com.blog.dto.CategoryVO;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.service.CategoryService;
import com.blog.util.CacheUtil;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    /** 设计规格 §8 定义的键，勿改。 */
    static final String CACHE_KEY = "blog:category:list";

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final CacheUtil cacheUtil;

    public CategoryServiceImpl(CategoryMapper categoryMapper,
                               ArticleMapper articleMapper,
                               CacheUtil cacheUtil) {
        this.categoryMapper = categoryMapper;
        this.articleMapper = articleMapper;
        this.cacheUtil = cacheUtil;
    }

    @Override
    public List<CategoryVO> list() {
        List<CategoryVO> cached = cacheUtil.getList(CACHE_KEY, CategoryVO.class);
        if (cached != null) {
            return cached;
        }
        List<CategoryVO> list = categoryMapper.selectCategoryWithArticleCount();
        cacheUtil.set(CACHE_KEY, list, CACHE_TTL);
        return list;
    }

    @Override
    public Long create(CategorySaveDTO dto) {
        throw new UnsupportedOperationException("任务 8 实现");
    }

    @Override
    public void update(CategorySaveDTO dto) {
        throw new UnsupportedOperationException("任务 8 实现");
    }

    @Override
    public void delete(Long categoryId) {
        throw new UnsupportedOperationException("任务 8 实现");
    }
}
```

- [ ] **步骤 6：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CategoryServiceImplTest
```
预期：PASS，3 个用例通过。

- [ ] **步骤 7：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "feat: category 读链路——含文章数查询与 Redis 缓存

列表走 blog:category:list 缓存（TTL 5 分钟），空集也缓存以避免穿透。
写方法暂抛 UnsupportedOperationException，下一个 commit 补齐。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 任务 8：category 写链路（新增 / 更新 / 逻辑删除 + 缓存失效）

**文件：**
- 创建：`backend/src/main/java/com/blog/dto/CategorySaveDTO.java`（若任务 7 步骤 4 已建则跳过）
- 创建：`backend/src/main/java/com/blog/dto/ValidateGroups.java`
- 修改：`backend/src/main/java/com/blog/service/impl/CategoryServiceImpl.java`（替换 3 个 `UnsupportedOperationException`）
- 测试：`backend/src/test/java/com/blog/service/impl/CategoryServiceImplTest.java`（追加用例）

- [ ] **步骤 1：追加失败的测试用例到 `CategoryServiceImplTest.java`**

在 `CategoryServiceImplTest` 类内追加以下内容（import 需相应补充：`com.blog.common.BizException`、`com.blog.dto.CategorySaveDTO`、`com.blog.entity.Category`、`com.baomidou.mybatisplus.core.conditions.Wrapper`、`static org.assertj.core.api.Assertions.assertThatThrownBy`、`static org.mockito.ArgumentMatchers.any`、`static org.mockito.Mockito.doAnswer`）：

```java
    // ---------- 写链路 ----------

    private CategorySaveDTO saveDto(Long id, String name, Integer sortOrder) {
        CategorySaveDTO dto = new CategorySaveDTO();
        dto.setCategoryId(id);
        dto.setCategoryName(name);
        dto.setSortOrder(sortOrder);
        return dto;
    }

    @Test
    @DisplayName("新增：名称已存在抛 40002")
    void createRejectsDuplicateName() {
        given(categoryMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> service.create(saveDto(null, "技术", 0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40002));
    }

    @Test
    @DisplayName("新增：成功时返回自增主键并清除列表缓存")
    void createReturnsIdAndEvictsCache() {
        given(categoryMapper.selectCount(any())).willReturn(0L);
        doAnswer(invocation -> {
            ((Category) invocation.getArgument(0)).setCategoryId(9L);
            return 1;
        }).when(categoryMapper).insert(any(Category.class));

        Long id = service.create(saveDto(null, "技术", 5));

        assertThat(id).isEqualTo(9L);
        verify(cacheUtil).delete("blog:category:list");
    }

    @Test
    @DisplayName("新增：sortOrder 为空时落库为 0")
    void createDefaultsSortOrderToZero() {
        given(categoryMapper.selectCount(any())).willReturn(0L);

        service.create(saveDto(null, "技术", null));

        verify(categoryMapper).insert(org.mockito.ArgumentMatchers.argThat(
                c -> c.getSortOrder() == 0));
    }

    @Test
    @DisplayName("更新：分类不存在抛 40400")
    void updateRejectsMissingCategory() {
        given(categoryMapper.selectById(9L)).willReturn(null);

        assertThatThrownBy(() -> service.update(saveDto(9L, "技术", 0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40400));
    }

    @Test
    @DisplayName("更新：与另一个分类重名抛 40002")
    void updateRejectsNameTakenByAnotherCategory() {
        Category existing = new Category();
        existing.setCategoryId(9L);
        given(categoryMapper.selectById(9L)).willReturn(existing);
        given(categoryMapper.selectCount(any())).willReturn(1L);

        assertThatThrownBy(() -> service.update(saveDto(9L, "生活", 0)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40002));
    }

    @Test
    @DisplayName("更新：成功时清除列表缓存")
    void updateEvictsCache() {
        Category existing = new Category();
        existing.setCategoryId(9L);
        given(categoryMapper.selectById(9L)).willReturn(existing);
        given(categoryMapper.selectCount(any())).willReturn(0L);

        service.update(saveDto(9L, "技术", 3));

        verify(categoryMapper).updateById(any(Category.class));
        verify(cacheUtil).delete("blog:category:list");
    }

    @Test
    @DisplayName("删除：分类不存在抛 40400")
    void deleteRejectsMissingCategory() {
        given(categoryMapper.selectById(9L)).willReturn(null);

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40400));
    }

    @Test
    @DisplayName("删除：逻辑删除分类、把该分类下文章的 categoryId 置空、并清除列表缓存")
    void deleteClearsArticleReferencesAndEvictsCache() {
        Category existing = new Category();
        existing.setCategoryId(9L);
        given(categoryMapper.selectById(9L)).willReturn(existing);

        service.delete(9L);

        verify(categoryMapper).deleteById(9L);
        verify(articleMapper).clearCategoryId(9L);
        verify(cacheUtil).delete("blog:category:list");
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CategoryServiceImplTest
```
预期：FAIL —— 8 个写链路用例报 `UnsupportedOperationException: 任务 8 实现`。

- [ ] **步骤 3：创建 `CategorySaveDTO.java`（若任务 7 步骤 4 已建，跳过本步骤）**

```java
package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增/更新入参。
 * categoryId 仅在更新时必填，用校验分组区分两种场景：
 * 新增时 @Null（必须为空），更新时 @NotNull。
 */
@Data
public class CategorySaveDTO {

    @Null(groups = ValidateGroups.Create.class, message = "新增时不允许指定分类ID")
    @NotNull(groups = ValidateGroups.Update.class, message = "更新时必须指定分类ID")
    private Long categoryId;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名长度不能超过 50")
    private String categoryName;

    private Integer sortOrder;
}
```

- [ ] **步骤 4：创建 `ValidateGroups.java`**

```java
package com.blog.dto;

/** JSR-380 校验分组标记。接口内不放任何成员，仅作类型标签。 */
public final class ValidateGroups {

    private ValidateGroups() {
    }

    /** 新增场景：categoryId 必须为空。 */
    public interface Create {
    }

    /** 更新场景：categoryId 必须非空。 */
    public interface Update {
    }
}
```

- [ ] **步骤 5：替换 `CategoryServiceImpl` 中的三个方法**

把 `create` / `update` / `delete` 从 `throw new UnsupportedOperationException(...)` 替换为真实实现，并在类上补 `@Transactional` 相关 import：

```java
    @Override
    public Long create(CategorySaveDTO dto) {
        if (existsByName(dto.getCategoryName(), null)) {
            throw new BizException(ResultCode.CATEGORY_NAME_EXISTS);
        }
        Category entity = new Category();
        entity.setCategoryName(dto.getCategoryName());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        categoryMapper.insert(entity);
        cacheUtil.delete(CACHE_KEY);
        return entity.getCategoryId();
    }

    @Override
    public void update(CategorySaveDTO dto) {
        Category existing = categoryMapper.selectById(dto.getCategoryId());
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        if (existsByName(dto.getCategoryName(), dto.getCategoryId())) {
            throw new BizException(ResultCode.CATEGORY_NAME_EXISTS);
        }
        Category entity = new Category();
        entity.setCategoryId(dto.getCategoryId());
        entity.setCategoryName(dto.getCategoryName());
        entity.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        categoryMapper.updateById(entity);
        cacheUtil.delete(CACHE_KEY);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long categoryId) {
        Category existing = categoryMapper.selectById(categoryId);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "分类不存在");
        }
        // 逻辑删除（@TableLogic 生效）
        categoryMapper.deleteById(categoryId);
        // 该分类下文章的 categoryId 置空（设计规格 §4 关系约束），与上面同事务
        articleMapper.clearCategoryId(categoryId);
        cacheUtil.delete(CACHE_KEY);
    }

    /**
     * 分类名是否已被占用。
     * excludeId 用于更新场景排除自身。
     * 唯一性只由本方法保证：category 表刻意不建唯一索引（「列 + deleted」的组合唯一键只能
     * 容纳一行 deleted=1，撑不起删除历史，同名记录的第二次逻辑删除会抛 MySQL 1062）。
     * 已逻辑删除的记录不参与判重，因此删掉分类后可以同名重建（设计规格 §4）。
     */
    private boolean existsByName(String categoryName, Long excludeId) {
        LambdaQueryWrapper<Category> wrapper = Wrappers.<Category>lambdaQuery()
                .eq(Category::getCategoryName, categoryName);
        if (excludeId != null) {
            wrapper.ne(Category::getCategoryId, excludeId);
        }
        return categoryMapper.selectCount(wrapper) > 0;
    }
```

需要补充的 import：

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.blog.entity.Category;
import org.springframework.transaction.annotation.Transactional;
```

- [ ] **步骤 6：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CategoryServiceImplTest
```
预期：PASS，11 个用例通过（3 个读 + 8 个写）。

- [ ] **步骤 7：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "$(cat <<'EOF'
feat: category 写链路——新增/更新/逻辑删除

重名校验排除自身且不理会已逻辑删除记录（支持删后同名重建）；
删除在单事务内逻辑删分类并把该分类下文章 categoryId 置空；
三个写操作都失效 blog:category:list 缓存。
categoryId 的必填性用 JSR-380 校验分组区分新增与更新。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 9：管理员鉴权服务（登录 / 登出）

**文件：**
- 创建：`backend/src/main/java/com/blog/dto/LoginDTO.java`
- 创建：`backend/src/main/java/com/blog/dto/LoginVO.java`
- 创建：`backend/src/main/java/com/blog/service/AdminAuthService.java`
- 创建：`backend/src/main/java/com/blog/service/impl/AdminAuthServiceImpl.java`
- 测试：`backend/src/test/java/com/blog/service/impl/AdminAuthServiceImplTest.java`

- [ ] **步骤 1：编写失败的测试 `AdminAuthServiceImplTest.java`**

```java
package com.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.blog.common.BizException;
import com.blog.dto.LoginDTO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceImplTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin123";

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AdminTokenStore tokenStore;

    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAuthServiceImpl(jwtUtil, tokenStore, USERNAME, PASSWORD);
    }

    private LoginDTO login(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    @Test
    @DisplayName("登录：凭证正确时签发 token 并写入会话存储")
    void loginIssuesAndStoresToken() {
        given(jwtUtil.generate(USERNAME)).willReturn("signed-token");

        String token = service.login(login(USERNAME, PASSWORD));

        assertThat(token).isEqualTo("signed-token");
        verify(tokenStore).save("signed-token");
    }

    @Test
    @DisplayName("登录：密码错误抛 40101")
    void loginRejectsWrongPassword() {
        assertThatThrownBy(() -> service.login(login(USERNAME, "wrong")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40101));
    }

    @Test
    @DisplayName("登录：用户名错误抛 40101")
    void loginRejectsWrongUsername() {
        assertThatThrownBy(() -> service.login(login("other", PASSWORD)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40101));
    }

    @Test
    @DisplayName("登录：长度不同的用户名也抛 40101（常量时间比较不得因长度差异抛异常）")
    void loginRejectsUsernameOfDifferentLength() {
        assertThatThrownBy(() -> service.login(login("a-much-longer-username", PASSWORD)))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode()).isEqualTo(40101));
    }

    @Test
    @DisplayName("登出：清除会话存储，使 token 立即失效")
    void logoutClearsTokenStore() {
        service.logout();

        verify(tokenStore).clear();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AdminAuthServiceImplTest
```
预期：编译失败 —— `AdminAuthServiceImpl` 与 `LoginDTO` 不存在。

- [ ] **步骤 3：创建 `LoginDTO.java`**

```java
package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

- [ ] **步骤 4：创建 `LoginVO.java`**

```java
package com.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录返回。前端把它存进 localStorage 的 blog-admin-token。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;
}
```

- [ ] **步骤 5：创建 `AdminAuthService.java`**

```java
package com.blog.service;

import com.blog.dto.LoginDTO;

public interface AdminAuthService {

    /** 校验凭证，成功返回 JWT，失败抛 BizException(40101)。 */
    String login(LoginDTO dto);

    /** 登出，使当前 token 立即失效。 */
    void logout();
}
```

- [ ] **步骤 6：创建 `AdminAuthServiceImpl.java`**

```java
package com.blog.service.impl;

import com.blog.common.BizException;
import com.blog.common.ResultCode;
import com.blog.dto.LoginDTO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.AdminAuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 单管理员鉴权（设计规格 §13 YAGNI：不建 admin 表，凭证来自配置）。
 * 凭证比对用 MessageDigest.isEqual 做常量时间比较，避免通过响应耗时逐字符
 * 爆破用户名密码。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final JwtUtil jwtUtil;
    private final AdminTokenStore tokenStore;
    private final String adminUsername;
    private final String adminPassword;

    public AdminAuthServiceImpl(JwtUtil jwtUtil,
                                AdminTokenStore tokenStore,
                                @Value("${blog.admin.username}") String adminUsername,
                                @Value("${blog.admin.password}") String adminPassword) {
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public String login(LoginDTO dto) {
        boolean usernameOk = constantTimeEquals(dto.getUsername(), adminUsername);
        boolean passwordOk = constantTimeEquals(dto.getPassword(), adminPassword);
        // 短路放在最后：两个比较都要执行完，避免用户名错误时提前返回泄露信息
        if (!usernameOk || !passwordOk) {
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        String token = jwtUtil.generate(adminUsername);
        tokenStore.save(token);
        return token;
    }

    @Override
    public void logout() {
        tokenStore.clear();
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
```

- [ ] **步骤 7：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AdminAuthServiceImplTest
```
预期：PASS，5 个用例通过。

- [ ] **步骤 8：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "feat: 管理员鉴权服务

单管理员（配置驱动，不建 admin 表）。凭证用 MessageDigest.isEqual
做常量时间比较，避免时序侧信道。登出清除 Redis 会话键。

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## 任务 10：HTTP 层（三个 Controller + MockMvc 契约测试）

**文件：**
- 创建：`backend/src/main/java/com/blog/controller/CategoryController.java`
- 创建：`backend/src/main/java/com/blog/controller/AdminCategoryController.java`
- 创建：`backend/src/main/java/com/blog/controller/AdminAuthController.java`
- 创建：`backend/src/main/java/com/blog/config/OpenApiConfig.java`
- 测试：`backend/src/test/java/com/blog/controller/CategoryControllerTest.java`
- 测试：`backend/src/test/java/com/blog/controller/AdminAuthControllerTest.java`

- [ ] **步骤 1：编写失败的测试 `CategoryControllerTest.java`**

```java
package com.blog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.dto.CategoryVO;
import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {CategoryController.class, AdminCategoryController.class})
@ActiveProfiles("test")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AdminTokenStore adminTokenStore;

    private void givenValidToken() {
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(adminTokenStore.matches("good-token")).willReturn(true);
    }

    @Test
    @DisplayName("公开列表：返回 code=0，data 元素含 articleCount")
    void listReturnsArticleCount() throws Exception {
        CategoryVO vo = new CategoryVO();
        vo.setCategoryId(1L);
        vo.setCategoryName("技术");
        vo.setSortOrder(0);
        vo.setArticleCount(3);
        given(categoryService.list()).willReturn(List.of(vo));

        mockMvc.perform(get("/api/category/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].categoryId").value(1))
                .andExpect(jsonPath("$.data[0].categoryName").value("技术"))
                .andExpect(jsonPath("$.data[0].articleCount").value(3));
    }

    @Test
    @DisplayName("管理端新增：无 token 时 HTTP 200 且 code=40100")
    void adminCreateWithoutTokenIsRejected() throws Exception {
        // HTTP 200 是断言的一部分：前端 axios 只在成功回调里读取 40100
        mockMvc.perform(post("/api/admin/category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"技术\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("管理端新增：带有效 token 时返回新建的 categoryId")
    void adminCreateWithTokenSucceeds() throws Exception {
        givenValidToken();
        given(categoryService.create(any())).willReturn(9L);

        mockMvc.perform(post("/api/admin/category")
                        .header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"技术\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(9));

        verify(categoryService).create(any());
    }

    @Test
    @DisplayName("管理端新增：分类名为空时参数校验失败 code=40001")
    void adminCreateValidatesBlankName() throws Exception {
        givenValidToken();

        mockMvc.perform(post("/api/admin/category")
                        .header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("categoryName")));
    }

    @Test
    @DisplayName("管理端新增：请求体带 categoryId 时校验失败（新增场景该字段必须为空）")
    void adminCreateRejectsProvidedId() throws Exception {
        givenValidToken();

        mockMvc.perform(post("/api/admin/category")
                        .header("Authorization", "Bearer good-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":5,\"categoryName\":\"技术\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("管理端删除：带有效 token 时成功")
    void adminDeleteWithTokenSucceeds() throws Exception {
        givenValidToken();

        mockMvc.perform(delete("/api/admin/category/9")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(categoryService).delete(9L);
    }

    @Test
    @DisplayName("管理端删除：无 token 时被拦截，Service 不被调用")
    void adminDeleteWithoutTokenDoesNotReachService() throws Exception {
        mockMvc.perform(delete("/api/admin/category/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));

        verify(categoryService, org.mockito.Mockito.never()).delete(any());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CategoryControllerTest
```
预期：FAIL / 编译失败 —— Controller 不存在。

- [ ] **步骤 3：创建 `CategoryController.java`**

```java
package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.CategoryVO;
import com.blog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台公开的分类接口。
 * 路径显式带 /api 前缀：前端 vite 代理 '/api' 无 rewrite，原样转发到本服务的 18088 端口。
 */
@Tag(name = "分类（前台）")
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分类列表（含文章数，走缓存）")
    @GetMapping("/list")
    public Result<List<CategoryVO>> list() {
        return Result.ok(categoryService.list());
    }
}
```

- [ ] **步骤 4：创建 `AdminCategoryController.java`**

```java
package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.CategorySaveDTO;
import com.blog.dto.ValidateGroups;
import com.blog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端分类接口。整个 /api/admin/** 由 AdminAuthInterceptor 保护。 */
@Tag(name = "分类（管理端）")
@RestController
@RequestMapping("/api/admin/category")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "新增分类，返回新建的分类 ID")
    @PostMapping
    public Result<Long> create(@Validated(ValidateGroups.Create.class) @RequestBody CategorySaveDTO dto) {
        return Result.ok(categoryService.create(dto));
    }

    @Operation(summary = "更新分类")
    @PutMapping
    public Result<Void> update(@Validated(ValidateGroups.Update.class) @RequestBody CategorySaveDTO dto) {
        categoryService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "逻辑删除分类，并把该分类下文章的分类置空")
    @DeleteMapping("/{categoryId}")
    public Result<Void> delete(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return Result.ok();
    }
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=CategoryControllerTest
```
预期：PASS，7 个用例通过。

> 若报找不到 `@MockitoBean`，说明 Spring 版本低于 6.2 —— 检查 `pom.xml` 中 parent 是否为 `3.4.3`。**不要退回到已废弃的 `@MockBean`**，先确认版本。

- [ ] **步骤 6：编写失败的测试 `AdminAuthControllerTest.java`**

```java
package com.blog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.security.AdminTokenStore;
import com.blog.security.JwtUtil;
import com.blog.service.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminAuthController.class)
@ActiveProfiles("test")
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private AdminTokenStore adminTokenStore;

    @Test
    @DisplayName("登录：成功返回 code=0 且 data.token 存在")
    void loginReturnsToken() throws Exception {
        given(adminAuthService.login(any())).willReturn("signed-token");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("signed-token"));
    }

    @Test
    @DisplayName("登录：入参为空时参数校验失败 code=40001")
    void loginValidatesBlankFields() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("登录接口本身不受鉴权拦截（无需 token 即可访问）")
    void loginIsExemptFromAuthInterceptor() throws Exception {
        given(adminAuthService.login(any())).willReturn("signed-token");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("登出：无 token 时被拦截 code=40100")
    void logoutRequiresToken() throws Exception {
        mockMvc.perform(post("/api/admin/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("登出：带有效 token 时成功并清除会话")
    void logoutWithTokenClearsSession() throws Exception {
        given(jwtUtil.verify("good-token")).willReturn("admin");
        given(adminTokenStore.matches("good-token")).willReturn(true);

        mockMvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(adminAuthService).logout();
    }
}
```

- [ ] **步骤 7：创建 `AdminAuthController.java`**

```java
package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.LoginDTO;
import com.blog.dto.LoginVO;
import com.blog.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端登录/登出。
 * login 在 WebMvcConfig 中被排除出鉴权拦截范围；logout 需要 token。
 */
@Tag(name = "管理员鉴权")
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Operation(summary = "登录，返回 JWT")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(new LoginVO(adminAuthService.login(dto)));
    }

    @Operation(summary = "登出，使当前 token 立即失效")
    @PostMapping("/logout")
    public Result<Void> logout() {
        adminAuthService.logout();
        return Result.ok();
    }
}
```

- [ ] **步骤 8：运行测试验证通过**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q test -Dtest=AdminAuthControllerTest
```
预期：PASS，5 个用例通过。

- [ ] **步骤 9：创建 `OpenApiConfig.java`**

```java
package com.blog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 元信息。UI 在 /swagger-ui.html，JSON 在 /v3/api-docs。
 * 导出该 JSON 是为了统一交付接口文档（Apifox 等工具可导入），
 * 避免手工维护接口清单与代码脱节。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI myBlogOpenApi() {
        return new OpenAPI().info(new Info()
                .title("我的博客系统 API")
                .description("个人博客后端接口。管理端接口需在 Authorization 头携带 Bearer token。"
                        + "所有响应 HTTP 状态码均为 200，业务结果见响应体 code 字段。")
                .version("0.0.1-SNAPSHOT"));
    }
}
```

- [ ] **步骤 10：全量测试验证**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn -q clean test
```
预期：PASS，全部 11 个测试类通过。

- [ ] **步骤 11：Commit**

```bash
cd D:/projects/myblog && git add backend/src && git commit -m "$(cat <<'EOF'
feat: HTTP 层与 OpenAPI 元信息

分类公开列表 + 管理端增删改 + 管理员登录登出。
MockMvc 测试锁死两条契约：管理端无 token 时 HTTP 200 且 code=40100；
新增时带 categoryId 属参数校验失败（分组校验生效）。
springdoc 导出 OpenAPI 供统一交付接口文档。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 11：DDL 与本地配置模板交付

本任务无自动化测试 —— 交付物是给用户执行的 SQL 与配置模板。验证方式是「内容与设计规格 §7、§10 逐条对齐」。

**文件：**
- 创建：`backend/src/main/resources/db/schema.sql`
- 创建：`backend/config/application-local.yml`

- [ ] **步骤 1：创建 `schema.sql`**

```sql
-- 我的博客系统 建库建表脚本
-- 依据 docs/superpowers/specs/2026-09-20-backend-skeleton-design.md §7
-- 执行方式（在 mysql 客户端中）：source <此文件绝对路径>;
-- 或：mysql -u<user> -p < schema.sql
--
-- 约定：
--   1. 列名使用驼峰（与 Java 实体字段同名，免去映射配置）
--   2. 所有删除一律逻辑删除（deleted 0/1）；不建唯一索引——deleted 只有 0/1，
--      「列 + deleted」的组合唯一键只能容纳一行 deleted=1，撑不起删除历史。
--      唯一性由应用层查询保证（WHERE name = ? AND deleted = 0）；
--      下方保留同列的非唯一索引，仅供查询加速。删后同名可重建。
--   3. 不建物理外键：关系完整性由 Service 层保证，物理外键会阻碍同名重建
--   4. createdAt/updatedAt 的 DEFAULT / ON UPDATE 是兜底（手工 SQL 插入场景），
--      应用层由 MyBatis-Plus 的 MetaObjectHandler 填充

CREATE DATABASE IF NOT EXISTS `happyblog`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `happyblog`;

-- ---------------------------------------------------------------
-- article 文章表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `article` (
    `articleId`     BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
    `title`         VARCHAR(120) NOT NULL                          COMMENT '标题',
    `summary`       VARCHAR(300)          DEFAULT NULL              COMMENT '摘要',
    `content`       LONGTEXT              DEFAULT NULL              COMMENT 'Markdown 正文',
    `coverImage`    VARCHAR(255)          DEFAULT NULL              COMMENT '封面图 URL',
    `categoryId`    BIGINT                DEFAULT NULL              COMMENT '分类ID，删分类后置空',
    `status`        TINYINT      NOT NULL DEFAULT 0                COMMENT '0草稿/1公开/2私密',
    `isTop`         TINYINT      NOT NULL DEFAULT 0                COMMENT '置顶',
    `isRecommended` TINYINT      NOT NULL DEFAULT 0                COMMENT '首页推荐',
    `viewCount`     INT          NOT NULL DEFAULT 0                COMMENT '总浏览量',
    `createdAt`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT      NOT NULL DEFAULT 0                COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`articleId`),
    KEY `idx_article_status` (`status`, `isTop`, `createdAt`),
    KEY `idx_article_categoryId` (`categoryId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章表';

-- ---------------------------------------------------------------
-- category 分类表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
    `categoryId`   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `categoryName` VARCHAR(50) NOT NULL                COMMENT '分类名',
    `sortOrder`    INT         NOT NULL DEFAULT 0      COMMENT '排序，升序',
    `createdAt`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`categoryId`),
    KEY `idx_category_categoryName` (`categoryName`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

-- ---------------------------------------------------------------
-- tag 标签表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `tagId`     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tagName`   VARCHAR(50) NOT NULL                COMMENT '标签名',
    `createdAt` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`   TINYINT     NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`tagId`),
    KEY `idx_tag_tagName` (`tagName`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签表';

-- ---------------------------------------------------------------
-- articleTag 文章-标签关联表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `articleTag` (
    `id`        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `articleId` BIGINT   NOT NULL                COMMENT '文章ID',
    `tagId`     BIGINT   NOT NULL                COMMENT '标签ID',
    `createdAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`   TINYINT  NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`id`),
    KEY `idx_articleTag_articleId_tagId` (`articleId`, `tagId`, `deleted`),
    KEY `idx_articleTag_tagId` (`tagId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章标签关联表';

-- ---------------------------------------------------------------
-- friendLink 友链表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `friendLink` (
    `friendLinkId` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`         VARCHAR(50)  NOT NULL                COMMENT '站点名',
    `url`          VARCHAR(255) NOT NULL                COMMENT '站点地址',
    `avatar`       VARCHAR(255)          DEFAULT NULL   COMMENT '头像 URL',
    `description`  VARCHAR(200)          DEFAULT NULL   COMMENT '简介',
    `sortOrder`    INT          NOT NULL DEFAULT 0      COMMENT '排序，升序',
    `createdAt`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`friendLinkId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='友情链接表';

-- ---------------------------------------------------------------
-- siteConfig 站点配置表（主键为业务键，无自增列）
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `siteConfig` (
    `configKey`   VARCHAR(64) NOT NULL COMMENT '配置键',
    `configValue` TEXT                 COMMENT '配置值',
    `createdAt`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`configKey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站点配置表';

-- ---------------------------------------------------------------
-- notice 通知表
-- ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notice` (
    `noticeId`  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`     VARCHAR(120) NOT NULL                COMMENT '标题',
    `content`   TEXT                                 COMMENT '内容',
    `startsAt`  DATETIME     NOT NULL                COMMENT '生效时间起',
    `endsAt`    DATETIME              DEFAULT NULL   COMMENT '生效时间止，空表示长期有效',
    `createdAt` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updatedAt` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`   TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除：0未删/1已删',
    PRIMARY KEY (`noticeId`),
    KEY `idx_notice_startsAt_endsAt` (`startsAt`, `endsAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知表';
```

- [ ] **步骤 2：创建 `application-local.yml` 模板**

```yaml
# 本地开发配置 —— 此文件已被 .gitignore 排除，不会进版本库。
# Spring Boot 通过 application.yml 里的
#   spring.config.import: optional:file:./config/application-local.yml
# 加载本文件。
#
# 两个注意点：
#   1. 路径相对【启动时的工作目录】解析，所以必须在 backend/ 目录下启动后端，
#      否则会去找 <仓库根>/config/application-local.yml 而找不到。
#   2. 文件名必须正好是 application-local.yml —— import 指向的是确切文件，不是目录。
#      若文件名写错，Spring 会静默跳过（optional: 前缀），配置悄悄回落到默认值。
#
# 用法：把下面的值改成你本机 MySQL / Redis 的真实连接信息。

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/happyblog?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
    username: 请填写你的 MySQL 用户名
    password: 请填写你的 MySQL 密码
  data:
    redis:
      host: localhost
      port: 6379
      # 本机 Redis 未设密码则留空
      password:

# 管理员登录凭证（前端 /admin/login 用这对账号密码登录）
blog:
  admin:
    username: admin
    password: 请改成你自己的密码
  jwt:
    # 本地随便填一串足够长的随机字符即可
    secret: 请填写一串长度不少于 32 的随机字符
```

- [ ] **步骤 3：确认 gitignore 生效**

运行：
```bash
cd D:/projects/myblog && git check-ignore -v backend/config/application-local.yml
```
预期：输出包含 `.gitignore:` 与匹配行号，说明该文件确实被忽略。**若命令无输出（未被忽略），回到任务 1 步骤 13 补 `.gitignore`。**

- [ ] **步骤 4：Commit**

```bash
cd D:/projects/myblog && git add backend/src/main/resources/db backend/config && git commit -m "$(cat <<'EOF'
feat: 建库建表 SQL 与本地配置模板

7 张表全部落地，列名用驼峰以对齐 Java 实体；不建唯一索引（唯一性由应用层
查询保证），删后同名可重建；不建物理外键。application-local.yml 为模板，
已被 gitignore 排除，由你填入真实连接串。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 12：冒烟脚本交付

本任务无自动化测试 —— 脚本由用户执行。验证方式是「脚本内的预期输出注释与设计规格 §9 的行为逐条对齐」。

**文件：**
- 创建：`backend/smoke/category-smoke.sh`

- [ ] **步骤 1：创建 `category-smoke.sh`**

```bash
#!/usr/bin/env bash
# category 竖切链路冒烟脚本
# 依据 docs/superpowers/specs/2026-09-20-backend-skeleton-design.md §12.4
#
# 前置条件：
#   1. 已执行 backend/src/main/resources/db/schema.sql 建库建表
#   2. 已填写 backend/config/application-local.yml
#   3. Redis 已启动
#   4. 后端已启动（见下方启动命令）
#
# 启动命令（另开一个终端）：
#   export JAVA_HOME=/e/works/jdk21
#   export PATH="$JAVA_HOME/bin:$PATH"
#   cd D:/projects/myblog/backend
#   /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn spring-boot:run -Dspring-boot.run.profiles=local
#
# 运行本脚本：
#   bash backend/smoke/category-smoke.sh
#
# 依赖 jq 解析 JSON。若未安装 jq，可去掉 jq 部分人工阅读原始输出。

set -u

BASE="${BASE:-http://localhost:18088}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"
CATEGORY_NAME="冒烟测试分类"

pass_count=0
fail_count=0

# 断言响应体中的 code 字段等于期望值
# 用法：expect_code <步骤说明> <实际响应体> <期望code>
expect_code() {
  local desc="$1" body="$2" want="$3"
  local got
  got=$(echo "$body" | jq -r '.code' 2>/dev/null)
  if [ "$got" = "$want" ]; then
    echo "  [通过] $desc（code=$got）"
    pass_count=$((pass_count + 1))
  else
    echo "  [失败] $desc —— 期望 code=$want，实际 code=$got"
    echo "         原始响应：$body"
    fail_count=$((fail_count + 1))
  fi
}

echo "=========================================="
echo " category 竖切链路冒烟"
echo " BASE=$BASE"
echo "=========================================="

# ---------------------------------------------------------------
echo
echo "步骤 1：管理员登录"
# 预期：code=0，data.token 为非空字符串
LOGIN_BODY=$(curl -s -X POST "$BASE/api/admin/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
expect_code "登录成功" "$LOGIN_BODY" "0"
TOKEN=$(echo "$LOGIN_BODY" | jq -r '.data.token // empty')
if [ -z "$TOKEN" ]; then
  echo "  [致命] 未取到 token，后续步骤无法继续。请确认 application-local.yml 里的"
  echo "         blog.admin.username / blog.admin.password 与脚本传入的一致。"
  exit 1
fi

# ---------------------------------------------------------------
echo
echo "步骤 2：不带 token 访问管理端接口"
# 预期：HTTP 状态码 200，响应体 code=40100
# 这条同时验证两件事：鉴权确实拦住了，且拒绝时没有返回 HTTP 401
# （前端 axios 只在成功回调里读 40100，返回 401 会让前端清理登录态的逻辑失效）
NO_TOKEN_FILE=$(mktemp)
NO_TOKEN_HTTP=$(curl -s -o "$NO_TOKEN_FILE" -w '%{http_code}' -X POST "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -d "{\"categoryName\":\"$CATEGORY_NAME\"}")
NO_TOKEN_RESP=$(cat "$NO_TOKEN_FILE")
rm -f "$NO_TOKEN_FILE"

if [ "$NO_TOKEN_HTTP" = "200" ]; then
  echo "  [通过] 无 token 请求返回 HTTP 200（前端才能读到业务码）"
  pass_count=$((pass_count + 1))
else
  echo "  [失败] 无 token 请求返回 HTTP $NO_TOKEN_HTTP，期望 200"
  echo "         若为 401，说明拦截器用了 response.setStatus(401) —— 必须改回 200"
  fail_count=$((fail_count + 1))
fi
expect_code "无 token 被拒" "$NO_TOKEN_RESP" "40100"

# ---------------------------------------------------------------
echo
echo "步骤 3：带 token 新增分类「$CATEGORY_NAME」"
# 预期：code=0，data 为新建的 categoryId（正整数）
CREATE_BODY=$(curl -s -X POST "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"categoryName\":\"$CATEGORY_NAME\",\"sortOrder\":99}")
expect_code "新增分类" "$CREATE_BODY" "0"
CATEGORY_ID=$(echo "$CREATE_BODY" | jq -r '.data // empty')
echo "         新建的 categoryId = $CATEGORY_ID"

# ---------------------------------------------------------------
echo
echo "步骤 4：公开列表应含该分类且 articleCount=0"
# 预期：code=0，列表中存在 categoryName=$CATEGORY_NAME 且 articleCount=0
LIST_BODY=$(curl -s "$BASE/api/category/list")
expect_code "查询列表" "$LIST_BODY" "0"
COUNT=$(echo "$LIST_BODY" | jq -r --arg n "$CATEGORY_NAME" '.data[] | select(.categoryName==$n) | .articleCount')
if [ "$COUNT" = "0" ]; then
  echo "  [通过] 列表中该分类存在且 articleCount=0（说明 LEFT JOIN 含文章数查询正确）"
  pass_count=$((pass_count + 1))
else
  echo "  [失败] 期望 articleCount=0，实际 '$COUNT'"
  fail_count=$((fail_count + 1))
fi

# ---------------------------------------------------------------
echo
echo "步骤 5：更新分类名称与排序"
# 预期：code=0
UPDATE_BODY=$(curl -s -X PUT "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"categoryId\":$CATEGORY_ID,\"categoryName\":\"${CATEGORY_NAME}改\",\"sortOrder\":1}")
expect_code "更新分类" "$UPDATE_BODY" "0"

# ---------------------------------------------------------------
echo
echo "步骤 6：列表应反映更新（验证缓存失效确实生效）"
# 预期：能找到新名称，且找不到旧名称
LIST_BODY_2=$(curl -s "$BASE/api/category/list")
NEW_HIT=$(echo "$LIST_BODY_2" | jq -r --arg n "${CATEGORY_NAME}改" '.data[] | select(.categoryName==$n) | .categoryId')
OLD_HIT=$(echo "$LIST_BODY_2" | jq -r --arg n "$CATEGORY_NAME" '.data[] | select(.categoryName==$n) | .categoryId')
if [ "$NEW_HIT" = "$CATEGORY_ID" ] && [ -z "$OLD_HIT" ]; then
  echo "  [通过] 新名称已生效、旧名称已消失（blog:category:list 缓存已被清除）"
  pass_count=$((pass_count + 1))
else
  echo "  [失败] 新名称命中='$NEW_HIT'（期望 $CATEGORY_ID），旧名称命中='$OLD_HIT'（期望空）"
  echo "         若新名称未生效，说明写操作后没有清除缓存"
  fail_count=$((fail_count + 1))
fi

# ---------------------------------------------------------------
echo
echo "步骤 7：重复新增同名分类"
# 预期：code=40002（分类名已存在）
DUP_BODY=$(curl -s -X POST "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"categoryName\":\"${CATEGORY_NAME}改\",\"sortOrder\":2}")
expect_code "重名校验" "$DUP_BODY" "40002"

# ---------------------------------------------------------------
echo
echo "步骤 8：逻辑删除该分类"
# 预期：code=0
DELETE_BODY=$(curl -s -X DELETE "$BASE/api/admin/category/$CATEGORY_ID" \
  -H "Authorization: Bearer $TOKEN")
expect_code "删除分类" "$DELETE_BODY" "0"

# ---------------------------------------------------------------
echo
echo "步骤 9：列表应不再包含该分类"
# 预期：查不到该分类
LIST_BODY_3=$(curl -s "$BASE/api/category/list")
GONE=$(echo "$LIST_BODY_3" | jq -r --arg n "${CATEGORY_NAME}改" '.data[] | select(.categoryName==$n) | .categoryId')
if [ -z "$GONE" ]; then
  echo "  [通过] 已删除的分类不再出现在列表中"
  pass_count=$((pass_count + 1))
else
  echo "  [失败] 已删除的分类仍出现在列表中（categoryId=$GONE）"
  fail_count=$((fail_count + 1))
fi

# ---------------------------------------------------------------
echo
echo "步骤 10：登出"
# 预期：code=0
LOGOUT_BODY=$(curl -s -X POST "$BASE/api/admin/logout" \
  -H "Authorization: Bearer $TOKEN")
expect_code "登出" "$LOGOUT_BODY" "0"

# ---------------------------------------------------------------
echo
echo "步骤 11：用登出前的 token 再次访问管理端"
# 预期：code=40100（登出后 token 立即失效；仅靠 JWT 签名无法做到这点）
AFTER_LOGOUT_BODY=$(curl -s -X POST "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"categoryName\":\"登出后不应创建成功\"}")
expect_code "登出后 token 立即失效" "$AFTER_LOGOUT_BODY" "40100"

# ---------------------------------------------------------------
echo
echo "=========================================="
echo " 结果：通过 $pass_count 项，失败 $fail_count 项"
echo "=========================================="

cat <<SQL

请再用 MySQL 客户端执行以下两条 SQL 核对落库结果（脚本无法代劳）：

  1) SELECT categoryId, categoryName, deleted FROM category
      WHERE categoryName IN ('$CATEGORY_NAME', '${CATEGORY_NAME}改');
     预期：一行，categoryName='${CATEGORY_NAME}改'，deleted=1

  2) SELECT articleId, categoryId FROM article WHERE categoryId = $CATEGORY_ID;
     预期：无结果。若你此前给该分类挂过文章，则那些文章的 categoryId 应为 NULL

SQL

exit $([ "$fail_count" -eq 0 ] && echo 0 || echo 1)
```

- [ ] **步骤 2：确认脚本语法正确**

运行：
```bash
bash -n D:/projects/myblog/backend/smoke/category-smoke.sh && echo "语法检查通过"
```
预期：输出「语法检查通过」。若报语法错误，修正后重跑。

- [ ] **步骤 3：确认脚本有可执行位（在 Git 中记录）**

运行：
```bash
cd D:/projects/myblog && git update-index --chmod=+x backend/smoke/category-smoke.sh 2>/dev/null; ls -l backend/smoke/category-smoke.sh
```

- [ ] **步骤 4：Commit**

```bash
cd D:/projects/myblog && git add backend/smoke && git commit -m "$(cat <<'EOF'
test: category 竖切链路冒烟脚本

11 步 curl 冒烟，覆盖登录、无 token 被拒（含 HTTP 200 断言）、
增删改查、重名校验、缓存失效、登出后 token 立即失效。
末尾提示用户用 SQL 核对逻辑删除落库与文章外键置空。

Co-Authored-By: Claude Code <noreply@anthropic.com>
EOF
)"
```

---

## 任务 13：收尾验证与交付

**文件：**
- 修改：`README.md`
- 修改：`docs/superpowers/specs/2026-09-17-myblog-design.md`

- [ ] **步骤 1：全量构建与测试**

运行：
```bash
cd D:/projects/myblog/backend && JAVA_HOME=/e/works/jdk21 PATH=/e/works/jdk21/bin:$PATH /e/works/apache-maven-3.8.6-bin/apache-maven-3.8.6/bin/mvn clean package
```
预期：`BUILD SUCCESS`，全部测试通过，`target/myblog-backend-0.0.1-SNAPSHOT.jar` 产出。

**如果任何测试失败，先修好再继续。不要把失败留到交付。**

- [ ] **步骤 2：确认交付物齐备**

运行：
```bash
cd D:/projects/myblog && ls -l backend/src/main/resources/db/schema.sql backend/config/application-local.yml backend/smoke/category-smoke.sh && find backend/src -name '*.java' | wc -l
```
预期：三个文件都存在；Java 文件数 ≈ 40（含测试）。

- [ ] **步骤 3：确认没有把本地配置误入库**

运行：
```bash
cd D:/projects/myblog && git ls-files backend/config/ && echo "--- 以上应为空 ---" && git status --short
```
预期：`git ls-files backend/config/` **无输出**（本地配置未被跟踪）。

- [ ] **步骤 4：更新 `README.md` 状态行**

把 README 末尾的：

```markdown
> 状态：需求讨论中，尚未编写业务代码。
```

替换为：

```markdown
> 状态：前端骨架（主题/路由/布局/首页 v2）与后端骨架（Spring Boot 3 + 公共基建 + JWT 鉴权 + category 竖切链路）已完成。
> 下一步：其余业务表的 CRUD、前台接口与缓存策略、管理端页面、RustFS 上传、Docker Compose 编排。
```

- [ ] **步骤 5：更新主规格 §15 序列进度**

在 `docs/superpowers/specs/2026-09-17-myblog-design.md` 的 §15 列表中，把第 2 步与第 3 步标注进度：

```markdown
2. 后端骨架（Spring Boot + MyBatis-Plus + 公共字段/逻辑删除/统一返回/全局异常）✅（2026-09-20 完成，含极简 JWT 鉴权与 OpenAPI 配置就绪；导出需运行应用）
3. 数据模型与基础 CRUD（分类/标签/文章）—— 全部 7 张表的 DDL/实体/Mapper 已就位；`category` 一条 CRUD 端到端已完成并测试覆盖，其余表待实现
```

- [ ] **步骤 6：Commit**

```bash
cd D:/projects/myblog && git add README.md docs/superpowers/specs/2026-09-17-myblog-design.md && git commit -m "docs: 后端骨架收尾——README 状态与主规格序列进度同步

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **步骤 7：向用户交付并说明验证边界**

向用户输出以下内容（**必须原样包含「未验证」的声明，不得含糊**）：

1. **已完成且已验证**：`mvn clean package` 通过；11 个测试类全部通过（编译、Service 层 Mockito 单测、Controller 层 MockMvc 契约与鉴权测试）
2. **未验证**：任何需要真实 MySQL / Redis 的行为 —— 包括逻辑删除是否真的写库、`articleCount` 的 JOIN 是否正确、Redis 缓存键是否按规格落地、登出失效、字段自动填充。这些由用户跑冒烟脚本确认
3. **待用户执行**：
   - 执行 `backend/src/main/resources/db/schema.sql` 建库建表
   - 填写 `backend/config/application-local.yml`
   - 启动本机 Redis（`E:\works\Redis\redis-server.exe`）
   - 按 `category-smoke.sh` 顶部注释启动后端
   - 运行 `bash backend/smoke/category-smoke.sh`，并把输出贴回
   - 执行脚本末尾提示的两条 SQL 核对落库
4. **若冒烟失败**：把完整输出贴回，据此定位修复

---

## 自检

### 1. 规格覆盖度

| 设计规格章节 | 对应任务 |
|--------------|---------|
| §4 依赖与版本 | 任务 1 步骤 1 |
| §5 包结构 | 任务 1-10 各步骤的创建路径 |
| §6 接口路径与前端契约 | 任务 10（含 HTTP 200 契约测试） |
| §6 错误码表 | 任务 1 步骤 2 + `ResultCodeTest` |
| §7 DDL | 任务 11 步骤 1 |
| §8 自动填充 | 任务 3 步骤 4 + `AuditMetaObjectHandlerTest` |
| §8 逻辑删除 | 任务 3 步骤 3（`@TableLogic`）+ 任务 1 步骤 7（全局配置） |
| §8 分页 | 任务 3 步骤 7 |
| §8 全局异常 | 任务 2 |
| §8 JWT 鉴权 | 任务 5 + 任务 6 |
| §8 缓存（两个键） | 任务 4 + 任务 7（`blog:category:list`）+ 任务 5（`blog:admin:token`） |
| §8 OpenAPI 导出 | 任务 10 步骤 9 |
| §9 category 全部接口 | 任务 7 + 任务 8 + 任务 10 |
| §10 配置与 gitignore | 任务 1 步骤 7-8、13 + 任务 11 |
| §11 测试策略（7 个测试类） | 任务 1、2、3、4、5、6、8、9、10 —— 共 11 个测试类，覆盖规格列出的全部 7 项 |
| §12 交付用户的部分 | 任务 11（DDL + 配置模板）+ 任务 12（冒烟脚本）+ 任务 13（启动命令与说明） |
| §13 完成标准 | 任务 13 步骤 1（构建）+ 步骤 7（交付边界声明） |

**无遗漏。**

### 2. 占位符扫描

- 无「待定」「TODO」「后续实现」等字样
- 唯一的临时实现是任务 7 的 `UnsupportedOperationException("任务 8 实现")`，**这是刻意的 TDD 中间态**：任务 8 步骤 5 明确要求替换，且任务 7 的测试只覆盖读链路，不会让违规实现蒙混过关
- 每个代码步骤都有完整可编译的代码块，无「类似任务 N」的省略

### 3. 类型一致性

逐个核对跨任务引用的类型与签名：

| 类型 / 方法 | 定义处 | 引用处 | 一致 |
|-------------|--------|--------|------|
| `Result.ok(T)` / `Result.fail(ResultCode)` / `Result.fail(int, String)` | 任务 1 步骤 3 | 任务 2、6、10 | ✅ |
| `ResultCode.getCode()` / `getMessage()` | 任务 1 步骤 2 | 任务 2、6、8、9 | ✅ |
| `BizException(ResultCode)` / `(ResultCode, String)` / `getCode()` | 任务 1 步骤 5 | 任务 2、8、9 | ✅ |
| `Category` 的 `getCreatedAt/getUpdatedAt/setDeleted/getSortOrder/setCategoryId` | 任务 3 步骤 3 | 任务 3 步骤 1 测试、任务 8 | ✅ |
| `CategoryVO` 的 `setCategoryId/setCategoryName/setSortOrder/setArticleCount` | 任务 3 步骤 6（提前创建） | 任务 4 测试、任务 7 测试、任务 10 测试 | ✅ |
| `CategorySaveDTO` 的 `setCategoryId/setCategoryName/setSortOrder` | 任务 8 步骤 3（提前在任务 7 步骤 4 创建） | 任务 7、8 | ✅ |
| `CacheUtil.get/getList/set/delete` | 任务 4 步骤 4 | 任务 5、7、8 | ✅ |
| `JwtUtil.generate/verify/getTtl` | 任务 5 步骤 3 | 任务 5 步骤 7、任务 9、任务 10 测试 | ✅ |
| `AdminTokenStore.KEY/save/matches/clear` | 任务 5 步骤 7 | 任务 5 测试、任务 9、任务 10 测试 | ✅ |
| `AdminAuthInterceptor.preHandle` | 任务 6 步骤 3 | 任务 6 测试、任务 6 步骤 5 | ✅ |
| `CategoryMapper.selectCategoryWithArticleCount()` | 任务 3 步骤 6 | 任务 7 | ✅ |
| `ArticleMapper.clearCategoryId(Long)` | 任务 3 步骤 6 | 任务 8 | ✅ |
| `CategoryService.list/create/update/delete` | 任务 7 步骤 4 | 任务 7、8、10 | ✅ |
| `CategoryServiceImpl(CategoryMapper, ArticleMapper, CacheUtil)` 构造签名 | 任务 7 步骤 5 | 任务 7/8 测试 | ✅ |
| `AdminAuthServiceImpl(JwtUtil, AdminTokenStore, String, String)` 构造签名 | 任务 9 步骤 6 | 任务 9 测试 | ✅ |
| `AdminTokenStore(CacheUtil, JwtUtil)` 构造签名 | 任务 5 步骤 7 | 任务 5 测试 | ✅ |
| `CacheUtil(RedisTemplate, ObjectMapper)` 构造签名 | 任务 4 步骤 4 | 任务 4 测试 | ✅ |
| `AdminAuthInterceptor(JwtUtil, AdminTokenStore, ObjectMapper)` 构造签名 | 任务 6 步骤 3 | 任务 6 测试 | ✅ |
| `ValidateGroups.Create` / `ValidateGroups.Update` | 任务 8 步骤 4 | 任务 8 步骤 3、任务 10 步骤 4 | ✅ |
| `AuditMetaObjectHandler` 被 `AuditMetaObjectHandlerTest` 直接 `new` | 任务 3 步骤 4 | 任务 3 步骤 1 | ✅ |

**跨任务依赖的两处特例已在计划中显式标注**（避免子代理按顺序执行时编译失败）：
- `CategoryVO` 在任务 3 步骤 6 提前创建（因 `CategoryMapper` 引用它），任务 7 步骤 3 标注「已建则跳过」
- `CategorySaveDTO` 在任务 7 步骤 4 提前创建（因 `CategoryService` 引用它），任务 8 步骤 3 标注「已建则跳过」
