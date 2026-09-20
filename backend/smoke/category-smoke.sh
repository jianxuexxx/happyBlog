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
# 依赖 jq 解析 JSON。脚本开头会检查 jq 是否存在，缺失时直接以中文提示退出。
#
# ===========================================================================
# 【证据一 · 步骤 6 / 11 / 12 的 GET /api/category/list】
#   任何一次成功的公开列表查询，都在端到端地证明三件事同时成立：
#     1) 真实应用能启动（不是单测上下文，不是 @WebMvcTest 切片）
#     2) 依赖注入完整（CategoryServiceImpl 真的拿到了 CategoryMapper）
#     3) 7 个 Mapper 接口都已注册（@MapperScan("com.blog.mapper") 生效）
#
#   为什么必须由本脚本兜住：任务 10 把 @MapperScan 从启动类搬到了
#   com.blog.config.MybatisPlusConfig。若它被挪回启动类（或漏掉），
#   *所有* 单测与切片测试依然全绿 —— 切片压根不扫那个普通 @Configuration；
#   而真实应用启动时 CategoryServiceImpl 注入 CategoryMapper 会直接抛
#   NoSuchBeanDefinitionException，应用根本起不来。
#   也就是说：单测全绿 + 应用起不来，是这条搬运的失败形态，
#   而本脚本里这几条「顺手过的列表查询」是它唯一的端到端证据。
#   —— 故它们不是附带步骤，它们本身就是验收项。
# ===========================================================================
# 【局限一】createdAt 是否由 AuditMetaObjectHandler 自动填充，HTTP 层面无法区分。
#   建表 SQL 里 createdAt 自身有 DEFAULT CURRENT_TIMESTAMP 兜底：
#   handler 生效与不生效，返回的 createdAt 都会有值。
#   故本脚本不对「handler 是否生效」下任何断言（详见步骤 6 注释）。
# 【局限二】「删分类后文章的 categoryId 置空」这条副作用，本脚本无法自动覆盖。
#   它由 Service 层 clearCategoryId 完成，需要库中确实存在引用该分类的文章行
#   才可观测，而 article 模块的 API 在本切片还不存在。
#   脚本只在删除步骤附近打印一段标注「需手工执行」的 SQL，由用户自行核对。
# ===========================================================================

set -u

# jq 是硬依赖。前置检查放在这里，避免失败被推迟到「取 token」那一步，
# 并打出误导性的「请确认 application-local.yml 里的…」提示
# —— 那种情况下用户会去查数据库配置，而真因只是没装 jq。
if ! command -v jq >/dev/null 2>&1; then
  echo "[致命] 未找到 jq。本脚本依赖 jq 解析 JSON 响应体，无法继续。"
  echo "       安装示例：apt-get install jq / brew install jq / choco install jq"
  echo "       （若确实无法安装，可按头部注释去掉 jq 部分，人工阅读原始输出）"
  exit 1
fi

BASE="${BASE:-http://localhost:18088}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"
CATEGORY_NAME="冒烟测试分类"
# OpenAPI 元信息期望值，取自 com.blog.config.OpenApiConfig
EXPECTED_API_TITLE="我的博客系统 API"

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

# 断言一个布尔条件成立（条件结果为 "1" 视为通过）
# 用法：expect_bool <步骤说明> <1或0>
expect_bool() {
  local desc="$1" ok="$2"
  if [ "$ok" = "1" ]; then
    echo "  [通过] $desc"
    pass_count=$((pass_count + 1))
  else
    echo "  [失败] $desc"
    fail_count=$((fail_count + 1))
  fi
}

# 【局限二】「删分类后文章 categoryId 置空」本切片无法用 HTTP 断言，
# 只能打印需手工执行的 SQL。用法：print_manual_article_check <刚删除的 categoryId>
print_manual_article_check() {
  cat <<SQL

  ------------- 需手工执行（本切片无法自动覆盖） -------------
  脚本无法断言「删分类后文章的 categoryId 置空」：该副作用由 Service 层
  clearCategoryId 完成，必须库中真的存在引用该分类的文章行才可观测，
  而 article 模块的 API 在本切片还不存在，没有 HTTP 入口能看出差异。
  请用 MySQL 客户端手工执行：

    SELECT articleId, categoryId FROM article WHERE categoryId = $1;
    预期：无结果；若你此前给该分类挂过文章，则那些文章的 categoryId 应为 NULL

  ----------------------------------------------------------
SQL
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
echo "步骤 3：拉取 OpenAPI 文档元信息"
# 预期：HTTP 状态码 200，且 info.title 等于 OpenApiConfig 里的期望值
# 该接口不需要 token（路径不在 /api/admin/** 之下，不会被拦截器覆盖）。
# OpenApiConfig 的产出此前零验证，而「交付 API 文档（可导入 Apifox）」是
# 任务 10 提交信息里写明的目标，故在此补上端到端验证。
API_DOCS_FILE=$(mktemp)
API_DOCS_HTTP=$(curl -s -o "$API_DOCS_FILE" -w '%{http_code}' "$BASE/v3/api-docs")
API_DOCS_BODY=$(cat "$API_DOCS_FILE")
rm -f "$API_DOCS_FILE"

if [ "$API_DOCS_HTTP" = "200" ]; then
  echo "  [通过] GET /v3/api-docs 返回 HTTP 200"
  pass_count=$((pass_count + 1))
else
  echo "  [失败] GET /v3/api-docs 返回 HTTP $API_DOCS_HTTP，期望 200"
  echo "         若为 404，检查 springdoc-openapi-starter-webmvc-ui 依赖与 springdoc.api-docs.path"
  fail_count=$((fail_count + 1))
fi

API_TITLE=$(echo "$API_DOCS_BODY" | jq -r '.info.title // empty')
if [ "$API_TITLE" = "$EXPECTED_API_TITLE" ]; then
  echo "  [通过] OpenAPI info.title='$API_TITLE'"
  pass_count=$((pass_count + 1))
else
  echo "  [失败] 期望 info.title='$EXPECTED_API_TITLE'，实际 '$API_TITLE'"
  fail_count=$((fail_count + 1))
fi

# ---------------------------------------------------------------
echo
echo "步骤 4：用错误的密码登录"
# 预期：HTTP 状态码 200（不是 401！），响应体 code=40101
# 这条具体路径（登录失败）此前没有任何用例走过，而它是前端登录页的主路径：
# 前端同样只在成功回调里读业务码，故 HTTP 状态必须是 200。
WRONG_PASS_FILE=$(mktemp)
WRONG_PASS_HTTP=$(curl -s -o "$WRONG_PASS_FILE" -w '%{http_code}' -X POST "$BASE/api/admin/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"__smoke_test_wrong_password__\"}")
WRONG_PASS_BODY=$(cat "$WRONG_PASS_FILE")
rm -f "$WRONG_PASS_FILE"

if [ "$WRONG_PASS_HTTP" = "200" ]; then
  echo "  [通过] 登录失败返回 HTTP 200（前端才能读到业务码 40101）"
  pass_count=$((pass_count + 1))
else
  echo "  [失败] 登录失败返回 HTTP $WRONG_PASS_HTTP，期望 200"
  fail_count=$((fail_count + 1))
fi
expect_code "密码错误被拒" "$WRONG_PASS_BODY" "40101"

# ---------------------------------------------------------------
echo
echo "步骤 5：带 token 新增分类「$CATEGORY_NAME」"
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
echo "步骤 6：公开列表应含该分类且 articleCount=0"
# 预期：code=0，列表中存在 categoryName=$CATEGORY_NAME 且 articleCount=0
# 【证据一】下面这次 GET /api/category/list 的成功本身即证明：
#   应用真的起来了 + CategoryMapper 注入成功 + 7 个 Mapper 均已注册
#   （@MapperScan 位于 MybatisPlusConfig 的等价性，见文件头说明）。
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

# 【新建分类确实可见】@TableLogic 的过滤条件是 deleted = 0：
# 若新建行的 deleted 是 NULL 而不是 0，这一行在列表里根本不会出现。
# 这一条同时也印证了「同名可重建」的前提 —— 应用层唯一性只看 deleted=0 的行。
VISIBLE_ID=$(echo "$LIST_BODY" | jq -r --arg id "$CATEGORY_ID" '.data[] | select((.categoryId|tostring)==$id) | .categoryId')
[ -n "$VISIBLE_ID" ] && VISIBLE_OK=1 || VISIBLE_OK=0
expect_bool "新建的分类出现在公开列表中（categoryId=$CATEGORY_ID 可见，说明 deleted=0 而非 NULL）" "$VISIBLE_OK"

# 【局限一 · 此处不断言】createdAt 是否有值区分不了 AuditMetaObjectHandler 是否生效：
# 建表 SQL 中 createdAt 自身带 DEFAULT CURRENT_TIMESTAMP 兜底，
# handler 生效与不生效都会有值，HTTP 层面无法分辨。故本步骤不做该断言。

# ---------------------------------------------------------------
echo
echo "步骤 7：更新分类名称与排序"
# 预期：code=0
UPDATE_BODY=$(curl -s -X PUT "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"categoryId\":$CATEGORY_ID,\"categoryName\":\"${CATEGORY_NAME}改\",\"sortOrder\":1}")
expect_code "更新分类" "$UPDATE_BODY" "0"

# ---------------------------------------------------------------
echo
echo "步骤 8：列表应反映更新（验证缓存失效确实生效）"
# 预期：能找到新名称，且找不到旧名称
# 【证据一】同上：这条成功的 GET /api/category/list 也是启动/DI/Mapper 注册的证据。
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
echo "步骤 9：重复新增同名分类"
# 预期：code=40002（分类名已存在）
DUP_BODY=$(curl -s -X POST "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"categoryName\":\"${CATEGORY_NAME}改\",\"sortOrder\":2}")
expect_code "重名校验" "$DUP_BODY" "40002"

# ---------------------------------------------------------------
echo
echo "步骤 10：逻辑删除该分类"
# 预期：code=0
# 【局限二】删除的另一条副作用（文章 categoryId 置空）无法在此用 HTTP 断言，
# 下方会打印需手工执行的 SQL —— 不要把它当成已覆盖。
DELETE_BODY=$(curl -s -X DELETE "$BASE/api/admin/category/$CATEGORY_ID" \
  -H "Authorization: Bearer $TOKEN")
expect_code "删除分类" "$DELETE_BODY" "0"
print_manual_article_check "$CATEGORY_ID"

# ---------------------------------------------------------------
echo
echo "步骤 11：列表应不再包含该分类"
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
echo "步骤 12：用同一个 categoryName 再次新增（删后同名可重建，必须成功）"
# 预期：code=0
# 步骤 12+13 合起来是「不建唯一索引、唯一性由应用层保证」这一裁定的端到端验证，
# 也是本次任务最有价值的一步。四步链路为：建（步骤 5+7）→ 删（步骤 10）
# → 同名再建（本步）→ 再删（步骤 13），四步各自都有断言。
REBUILD_BODY=$(curl -s -X POST "$BASE/api/admin/category" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"categoryName\":\"${CATEGORY_NAME}改\",\"sortOrder\":2}")
expect_code "删后同名重建" "$REBUILD_BODY" "0"
CATEGORY_ID_2=$(echo "$REBUILD_BODY" | jq -r '.data // empty')
echo "         重建的 categoryId = $CATEGORY_ID_2"

# ---------------------------------------------------------------
echo
echo "步骤 13：再次逻辑删除该分类（必须成功）"
# 预期：code=0
# 【这一步是缺陷回归证据】若建表沿用 UNIQUE KEY (categoryName, deleted)：
# 第一次删除把 ('…改', 0) 改成 ('…改', 1)；同名重建插入 ('…改', 0) 尚不冲突；
# 但第二次删除的 UPDATE ... SET deleted=1 会与既有的 ('…改', 1) 行撞唯一键，
# MySQL 直接报 1062，第二次删除必失败 —— 即删除历史撑不过 1 条。
# 现在 category 表只有 KEY idx_category_categoryName(categoryName, deleted) 普通索引，
# 唯一性由应用层 WHERE categoryName=? AND deleted=0 保证，故本步必须 code=0。
# 返回任何非 0 code（尤其 1062）都说明唯一索引又回来了。
DELETE_AGAIN_BODY=$(curl -s -X DELETE "$BASE/api/admin/category/$CATEGORY_ID_2" \
  -H "Authorization: Bearer $TOKEN")
expect_code "同名分类第二次删除" "$DELETE_AGAIN_BODY" "0"

# ---------------------------------------------------------------
echo
echo "步骤 14：登出"
# 预期：code=0
LOGOUT_BODY=$(curl -s -X POST "$BASE/api/admin/logout" \
  -H "Authorization: Bearer $TOKEN")
expect_code "登出" "$LOGOUT_BODY" "0"

# ---------------------------------------------------------------
echo
echo "步骤 15：用登出前的 token 再次访问管理端"
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
     预期：两行，categoryName 均为 '${CATEGORY_NAME}改'，deleted 均为 1
     （步骤 5 建→7 改名→10 删一行，步骤 12 同名重建→13 再删一行）

  2) SELECT articleId, categoryId FROM article WHERE categoryId = $CATEGORY_ID;
     预期：无结果。若你此前给该分类挂过文章，则那些文章的 categoryId 应为 NULL

SQL

exit $([ "$fail_count" -eq 0 ] && echo 0 || echo 1)
