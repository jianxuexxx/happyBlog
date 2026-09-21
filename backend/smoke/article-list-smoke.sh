#!/usr/bin/env bash
# article 列表接口冒烟脚本
# 依据 docs/superpowers/specs/2026-09-21-article-list-slice-design.md §7
#
# 前置条件：
#   1. 已执行 backend/src/main/resources/db/schema.sql 建库建表
#   2. 已填写 backend/config/application-local.yml
#   3. Redis 已启动
#   4. 后端已启动（启动命令见 category-smoke.sh 头部，与之相同）
#   5. 【第二层的前提，务必先读】库中 article 表除本脚本的样例数据（9901-9905）外，
#      **不存在其它文章**。理由是下面这四条查询不按 categoryId 限域，取值落在全库范围，
#      故只有在「全库只有样例数据」时断言值才成立：
#        · keyword=user_name → total=1（含「下划线被转义」那条，它同样断言 total=1）
#        · keyword=100%25    → total=1
#        · recommended=true  → total=1
#        · top=true          → total=1
#      一旦你通过管理端（本项目下一步）写了真实文章，只要其中有一篇的标题或摘要含
#      user_name / 100%，或它 isRecommended=1 / isTop=1，对应断言就会变红 ——
#      **那是你的库里有别的文章，不是接口坏了**（失败信息里会打出原始响应，可据此确认）。
#      届时给这四条查询各加上 &categoryId=$SMOKE_CID 限域即可；本脚本刻意不加，因为
#      这些数值与筛选条件是切片设计规格钉死的，改断言须同步改规格。
#      第二层其余断言都按 id 限域（categoryId=9900 / tagId=9911 / 9901 / 9902），不受本前提影响。
#
# 运行本脚本：
#   bash backend/smoke/article-list-smoke.sh
#
# ===========================================================================
# 【本脚本分两层，原因是一个硬事实】
#   本切片没有任何文章写入口（管理端 article CRUD 不在范围内），
#   所以真库 article 表是空的 —— 第一层不需要业务数据即可跑，
#   第二层的全部断言都需要先手工灌入样例数据（见文末 INSERT）。
#
# 【第一层证明了什么】
#   任何一次成功的 GET /api/article/list，都在端到端地证明：
#     1) 真实应用能启动（不是单测上下文，不是 @WebMvcTest 切片）
#     2) 依赖注入完整（ArticleServiceImpl 真的拿到了 ArticleMapper）
#     3) 7 个 Mapper 接口都已注册（com.blog.config.MybatisPlusConfig 上的 @MapperScan("com.blog.mapper") 生效）
#   与 category-smoke.sh 的【证据一】同一个道理：若 @MapperScan 被挪回启动类，
#   所有单测与切片依然全绿，而真实应用启动时直接抛 NoSuchBeanDefinitionException。
#
# 【第一层证明不了什么】
#   空库下 total=0、list=[]，与「接口写错了导致什么都查不到」在输出上无法区分。
#   真正的筛选、排序、翻页语义只有第二层能验。
#
# 【单测覆盖不到、只能靠本脚本第二层兜住的点】
#   tagId 的 EXISTS 子查询里用 article.articleId 引用外层表，依赖 MyBatis-Plus
#   生成不带别名的 FROM article。若升级 MP 后它改为生成别名，该片段会失效 ——
#   而单测断言的是 wrapper 里的字符串，不是数据库真跑的结果，发现不了。
#   故升级 MyBatis-Plus 后必须重跑第二层。
#
# 【本脚本另外要证伪的一件事：鉴权拦截器是否会误拦公开列表】
#   ArticleControllerTest#listIsPublicWithoutToken 用 verify(articleService).list(any())
#   证明了请求确实穿透到了 Service 层，但 @WebMvcTest 切片是否真的注册了
#   AdminAuthInterceptor，从测试代码里无法核实（切片不扫普通 @Configuration）。
#   本脚本跑在真实应用上：下面第一层「无参列表接口可访问」那条 curl **不带任何
#   Authorization 头**，若拦截器把 /api/article/list 也当成管理端路径拦下，
#   它会返回 40100 而不是 0，断言当场变红。这条断言是它的唯一端到端证据。
# ===========================================================================

set -u

# jq 是硬依赖，前置检查放在这里（理由与 category-smoke.sh 相同：避免失败被推迟到
# 某个后续步骤，并打出误导性的排查方向）。
if ! command -v jq >/dev/null 2>&1; then
  echo "[致命] 未找到 jq。本脚本依赖 jq 解析 JSON 响应体，无法继续。"
  echo "       安装示例：apt-get install jq / brew install jq / choco install jq"
  exit 1
fi

BASE="${BASE:-http://localhost:18088}"
CURL_OPTS=(--max-time 60 --connect-timeout 5)
# --max-time 必须 >= Hikari 的 connectionTimeout(默认 30s)：数据库不可达时应用要等到
# 第 30 秒才会用 code=50000 明确报错，超时值小于它会让 curl 先掐断连接，
# 断言拿到空响应体，把「响亮的错误」变成「误导性的空值」。
# --connect-timeout 5 只覆盖「连不上」（端口没起）的场景。

# ---------------------------------------------------------------------------
# 【Windows/Git Bash 编码契约 · 查询串篇】本脚本所有 query 参数一律只用 ASCII。
#   category-smoke.sh 头部那条契约覆盖的是「请求体走 stdin」，而 GET 的查询串
#   只能走 argv —— 它正好在 MSYS2 会转码的那条通路上。中文关键词会以 GBK 字节
#   发出、被服务端按 UTF-8 解码，结果恒为「匹配不到」，
#   看起来像搜索逻辑坏了，实际是编码问题（排查方向会被彻底带偏）。
#   要测中文关键词，须把 URL 里的中文**预先百分号编码**写死
#   （如 用户 → %E7%94%A8%E6%88%B7），不要指望 argv 能安全传中文。
#   这也是样例数据使用显式固定 id（9900 段）的原因：让筛选参数全是数字。
# ---------------------------------------------------------------------------

SMOKE_CID=9900     # 样例分类 categoryId
SMOKE_TAG=9911     # 样例标签 tagId

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

# 断言响应体的某个 jq 取值等于期望值（ASCII 值）
# 用法：expect_json <步骤说明> <响应体> <jq过滤表达式> <期望值>
expect_json() {
  local desc="$1" body="$2" filter="$3" want="$4"
  local got
  got=$(echo "$body" | jq -r "$filter" 2>/dev/null)
  if [ "$got" = "$want" ]; then
    echo "  [通过] $desc（$filter=$got）"
    pass_count=$((pass_count + 1))
  else
    echo "  [失败] $desc —— 期望 $filter=$want，实际 $got"
    echo "         原始响应：$body"
    fail_count=$((fail_count + 1))
  fi
}

get() { curl -s "${CURL_OPTS[@]}" "$BASE$1"; }

# ===========================================================================
echo "=========================================="
echo " article 列表接口冒烟"
echo " BASE=$BASE"
echo "=========================================="
echo "=== 第一层：不需要业务数据 ==="

# 【本步是鉴权拦截器的唯一端到端证据】下面的 get() 不带任何 Authorization 头。
# 若 AdminAuthInterceptor 误把 /api/article/list 当成管理端路径拦下，这里会是 40100 而非 0。
BODY=$(get "/api/article/list")
expect_code "无参列表接口可访问（不带 token 即 code=0，证明拦截器未误拦公开列表）" "$BODY" 0
for key in total page pageSize list; do
  expect_bool "响应 data 含 $key 字段" \
    "$(echo "$BODY" | jq -r "(.data | has(\"$key\")) | if . then 1 else 0 end")"
done
expect_bool "data.list 是数组" \
  "$(echo "$BODY" | jq -r '.data.list | if type == "array" then 1 else 0 end')"
expect_json "无参调用 page 默认 1"    "$BODY" '.data.page' 1
expect_json "无参调用 pageSize 默认 10" "$BODY" '.data.pageSize' 10

BODY=$(get "/api/article/list?pageSize=999")
expect_json "pageSize=999 被钳制为 50（回显钳制后的值）" "$BODY" '.data.pageSize' 50

BODY=$(get "/api/article/list?page=0&pageSize=0")
expect_json "page=0 被钳制为 1"     "$BODY" '.data.page' 1
expect_json "pageSize=0 被钳制为 1" "$BODY" '.data.pageSize' 1

BODY=$(get "/api/article/list?categoryId=abc")
expect_code "categoryId 非数字返回 40001" "$BODY" 40001
expect_bool "categoryId 非数字不得落到 50000（客户端错误被误报成服务器故障）" \
  "$([ "$(echo "$BODY" | jq -r '.code')" != "50000" ] && echo 1 || echo 0)"

BODY=$(get "/api/article/list?categoryId=999999")
expect_code "不存在的分类返回 code=0 而非 404" "$BODY" 0
expect_json "不存在的分类 total=0"            "$BODY" '.data.total' 0

BODY=$(get "/api/article/list?keyword=%20%20%20")
expect_code "纯空格 keyword 不报错（等同不传）" "$BODY" 0

# ===========================================================================
echo
echo "=== 第二层：需要先灌入样例数据 ==="

BODY=$(get "/api/article/list?categoryId=$SMOKE_CID")
if [ "$(echo "$BODY" | jq -r '.data.total')" = "0" ]; then
  echo "[跳过] 第二层：categoryId=$SMOKE_CID 下没有文章，样例数据尚未灌入。"
  echo "       请先执行本脚本末尾的 INSERT，再重跑本脚本。"
else
  # 样例数据共 5 篇：3 篇公开 + 1 草稿 + 1 私密。前台只能看到 3 篇。
  # 这里同时验证了「status 恒定条件」与 categoryId 筛选两件事。
  expect_json "该分类下前台可见 3 篇（草稿与私密被 status=1 挡掉）" \
    "$BODY" '.data.total' 3
  # 样例数据里草稿与私密的 viewCount 都是 0，三篇公开的分别是 10/20/30。
  # 结果里出现 viewCount=0 就说明草稿或私密泄漏进了前台列表。
  expect_json "结果里没有 viewCount=0 的文章（即草稿/私密未泄漏）" \
    "$BODY" '[.data.list[] | select(.viewCount == 0)] | length' 0
  # 置顶优先的判据：9901 是样例里唯一 isTop=1 的文章，而它的 createdAt **最旧**（5 天前）。
  # ArticleListVO 没有 isTop 字段（规格 §3），所以只能靠「最旧的那篇反而排最前」来证明
  # 置顶优先 —— 排序里一旦漏掉 isTop DESC，9901 会掉到最后一位。
  expect_json "置顶优先：createdAt 最旧的 9901 排第一" "$BODY" '.data.list[0].articleId' 9901
  expect_json "置顶文章的浏览量为 10（样例数据特征值）" "$BODY" '.data.list[0].viewCount' 10

  # 【本步兑现 ArticleServiceImpl 里 tagId EXISTS 处那条注释的承诺】
  #   该注释写明「单测发现不了（单测断言的是 wrapper 里的字符串，不是数据库真跑的结果），
  #   由冒烟脚本第二层的 tagId 筛选兜住」。这里就是那个「兜住」：只有真库真跑，
  #   article.articleId 这种不带别名的外层引用一旦因 MP 升级而失效，本步即变红。
  BODY=$(get "/api/article/list?tagId=$SMOKE_TAG")
  expect_json "tagId 命中所挂文章" "$BODY" '.data.total' 1
  # 这篇挂了两个标签。JOIN 写法会让它出现两次，EXISTS 天然去重 —— 这是本断言的真正内容。
  expect_json "挂两个标签的文章只出现一次（EXISTS 去重，不是 JOIN）" \
    "$BODY" '[.data.list[] | select(.articleId == 9902)] | length' 1

  BODY=$(get "/api/article/list?keyword=user_name")
  expect_json "keyword 命中摘要里的词（证明摘要也参与匹配）" "$BODY" '.data.total' 1
  # 未转义时 _ 匹配任意单字符，这条会命中全部文章（total 变成 3）。
  expect_bool "下划线被转义：不会命中全站" \
    "$([ "$(echo "$BODY" | jq -r '.data.total')" = "1" ] && echo 1 || echo 0)"

  BODY=$(get "/api/article/list?keyword=100%25")
  expect_json "百分号被转义：只命中含 100% 的那篇" "$BODY" '.data.total' 1

  BODY=$(get "/api/article/list?recommended=true")
  expect_json "recommended=true 只出推荐位那篇" "$BODY" '.data.total' 1
  expect_json "推荐位那篇的 viewCount 为 20"     "$BODY" '.data.list[0].viewCount' 20

  BODY=$(get "/api/article/list?top=true")
  expect_json "top=true 只出置顶那篇"       "$BODY" '.data.total' 1
  expect_json "置顶那篇的 viewCount 为 10" "$BODY" '.data.list[0].viewCount' 10

  # 翻页不重不漏：两页的 articleId 集合无交集，并集等于总数
  P1=$(get "/api/article/list?categoryId=$SMOKE_CID&pageSize=2&page=1")
  P2=$(get "/api/article/list?categoryId=$SMOKE_CID&pageSize=2&page=2")
  expect_bool "第 1、2 页的 articleId 无交集" \
    "$([ "$(jq -n --argjson a "$(echo "$P1" | jq '.data.list | map(.articleId)')" \
                   --argjson b "$(echo "$P2" | jq '.data.list | map(.articleId)')" \
                   '[$a[] | select(. as $x | $b | index($x))] | length')" = "0" ] && echo 1 || echo 0)"
  expect_bool "两页并集等于总数 3（翻页不遗漏）" \
    "$([ "$(jq -n --argjson a "$(echo "$P1" | jq '.data.list | map(.articleId)')" \
                   --argjson b "$(echo "$P2" | jq '.data.list | map(.articleId)')" \
                   '($a + $b) | unique | length')" = "3" ] && echo 1 || echo 0)"
fi

# ---------------------------------------------------------------
# 【第二层失败时的判断原则】先确认样例数据真的灌进去了：
#   SELECT COUNT(*) FROM article WHERE categoryId = 9900 AND deleted = 0;   -- 预期 5
# 再怀疑代码。空库下第二层每一条都会「红」，那不是缺陷。
# ---------------------------------------------------------------

echo
echo "=========== 汇总 ==========="
echo "通过 $pass_count 项，失败 $fail_count 项"
# 退出码刻意不在这里给：脚本头部（及上面「第二层」段）的指引是「见文末 INSERT」，
# 若此处提前 exit 1，失败时那段样例数据 SQL 就再也不打印了 —— 而指针恰好在最需要它
# 的时候断掉。故把退出码挪到文末 SQL 之后，见文件最后两行。

cat <<'SQL'

============================================================
 第二层所需的样例数据（手工执行；第一层不需要）
============================================================

-- ============ 第二层冒烟所需的样例数据（手工执行） ============
-- 前置：先执行 schema.sql。
--
-- 【为什么用显式固定 id（9900 段）而不是自增】
--   脚本里的筛选参数必须全是 ASCII：Git Bash 把 URL 里的中文转成 GBK 后再发给
--   原生 curl，服务端按 UTF-8 解码必然匹配不到（见脚本头部编码契约）。
--   所以脚本不能靠「按中文名字去查 id」，只能写死数字。9900 段是高位值，
--   正常使用的自增 id 短期内不会撞上。
--
-- 灌完后核对：
--   SELECT articleId, title, status, isTop, isRecommended, viewCount
--     FROM article WHERE categoryId = 9900 AND deleted = 0;
--   预期 5 行：3 篇 status=1（viewCount 10/20/30），1 篇 status=0，1 篇 status=2。
--
-- 列名必须与 backend/src/main/resources/db/schema.sql 逐字核对：本项目刻意关闭了
-- map-underscore-to-camel-case（见 application.yml 与 ColumnNamingConventionTest），
-- DDL 用的是驼峰列名。执行前先 DESC article; 确认，列名不符会直接报 1054 Unknown column。

INSERT INTO category (categoryId, categoryName, sortOrder, createdAt, updatedAt, deleted)
VALUES (9900, '冒烟分类', 99, NOW(), NOW(), 0);

-- 三篇公开 + 一篇草稿 + 一篇私密。
-- 9902 的摘要里刻意放了 user_name 与 100%，用来验证 LIKE 通配符转义。
INSERT INTO article (articleId, title, summary, content, coverImage, categoryId, status, isTop, isRecommended, viewCount, createdAt, updatedAt, deleted) VALUES
  (9901, '冒烟-置顶公开', '置顶的那一篇',                    '# 正文', '', 9900, 1, 1, 0, 10, NOW() - INTERVAL 5 DAY, NOW(), 0),
  (9902, '冒烟-普通公开', '普通的一篇',                      '# 正文', '', 9900, 1, 0, 1, 20, NOW() - INTERVAL 4 DAY, NOW(), 0),
  (9903, '冒烟-含关键词', '这篇摘要里有 user_name 和 100% 两个词', '# 正文', '', 9900, 1, 0, 0, 30, NOW() - INTERVAL 3 DAY, NOW(), 0),
  (9904, '冒烟-草稿不该出现', '草稿摘要',                    '# 正文', '', 9900, 0, 0, 0,  0, NOW() - INTERVAL 2 DAY, NOW(), 0),
  (9905, '冒烟-私密不该出现', '私密摘要',                    '# 正文', '', 9900, 2, 0, 0,  0, NOW() - INTERVAL 1 DAY, NOW(), 0);

-- 给 9902 挂两个标签：用来验证 EXISTS 不会让挂多标签的文章在结果里出现两次
-- （JOIN 写法会，这正是选 EXISTS 的理由）。
INSERT INTO tag (tagId, tagName, createdAt, updatedAt, deleted) VALUES
  (9911, '冒烟标签A', NOW(), NOW(), 0),
  (9912, '冒烟标签B', NOW(), NOW(), 0);

INSERT INTO articleTag (articleId, tagId, createdAt, updatedAt, deleted) VALUES
  (9902, 9911, NOW(), NOW(), 0),
  (9902, 9912, NOW(), NOW(), 0);

============================================================
 清理样例数据（第二层跑完后手工执行，别留在库里）
============================================================

DELETE FROM articleTag WHERE articleId = 9902;
DELETE FROM article    WHERE articleId BETWEEN 9901 AND 9905;
DELETE FROM tag        WHERE tagId     IN (9911, 9912);
DELETE FROM category   WHERE categoryId = 9900;

-- 这里是**物理删除**，与本项目「一律逻辑删除」的生产约定相反 —— 这是刻意的：
-- 样例数据不是业务数据，留在库里只会污染后续的手工核对与将来的真机数据。
-- 生产代码里任何地方都不得照抄这个写法。

SQL

# 退出码在最后单独给出，且必须是文件的最后两条命令：
#   · 有失败 → exit 1；全过 → exit 0（语义与原先一致）
#   · 上面 cat 的返回码不能覆盖退出码 —— 这正是末尾这行显式 exit 0 存在的理由，
#     请勿把它删掉或挪到 cat 之前
[ "$fail_count" -eq 0 ] || exit 1
exit 0
