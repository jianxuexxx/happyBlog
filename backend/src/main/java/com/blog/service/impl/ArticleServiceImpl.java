package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.PageResult;
import com.blog.dto.ArticleListVO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.entity.Article;
import com.blog.mapper.ArticleMapper;
import com.blog.service.ArticleService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ArticleServiceImpl implements ArticleService {

    /** 公开状态。前台列表恒定只出这一种（设计规格 §3）。 */
    static final int STATUS_PUBLIC = 1;

    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 10;
    /** 每页条数上限，防止 pageSize=999999 拖垮库（设计规格 §3）。 */
    static final int MAX_PAGE_SIZE = 50;

    private final ArticleMapper articleMapper;

    public ArticleServiceImpl(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @Override
    public PageResult<ArticleListVO> list(ArticleQueryDTO query) {
        int pageNum = normalizePage(query.getPage());
        int pageSize = normalizePageSize(query.getPageSize());

        LambdaQueryWrapper<Article> wrapper = Wrappers.lambdaQuery();
        // 恒定条件：草稿（0）与私密（2）永不进前台列表（主规格 §7）
        wrapper.eq(Article::getStatus, STATUS_PUBLIC);
        buildFilters(wrapper, query);
        // 末位的 articleId 是分页稳定性关键：createdAt 会重复（批量导入尤其常见），
        // 没有唯一键兜底时，同一条记录可能在翻页时出现两次或被整页跳过（设计规格 §3）。
        wrapper.orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getCreatedAt)
                .orderByDesc(Article::getArticleId);

        IPage<Article> result = articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // 回显钳制后的值，不是请求值：回显原值会让前端页码控件按请求值排页，
        // 与实际返回的条数对不上（设计规格 §3）。
        return PageResult.of(result.getTotal(), pageNum, pageSize, toVoList(result.getRecords()));
    }

    private void buildFilters(LambdaQueryWrapper<Article> wrapper, ArticleQueryDTO query) {
        wrapper.eq(query.getCategoryId() != null, Article::getCategoryId, query.getCategoryId());

        // recommended / top 是「加上这个约束」的开关：false 与 null 等价，都表示不筛选，
        // false 不是「筛选出非推荐的」。写成 != null 会把 false 当成真实条件（设计规格 §3）。
        wrapper.eq(Boolean.TRUE.equals(query.getRecommended()), Article::getIsRecommended, 1);
        wrapper.eq(Boolean.TRUE.equals(query.getTop()), Article::getIsTop, 1);

        String keyword = normalizeKeyword(query.getKeyword());
        // 必须用 and(...) 把 OR 包进括号。否则拼出来是
        //   WHERE status = 1 AND title LIKE ? OR summary LIKE ?
        // AND 优先级高于 OR，等同于 (status=1 AND title LIKE ?) OR summary LIKE ? ——
        // 摘要命中的草稿与私密文章会直接泄漏进前台列表（设计规格 §3 恒定条件）。
        wrapper.and(keyword != null, x -> x.like(Article::getTitle, keyword)
                .or().like(Article::getSummary, keyword));

        // EXISTS 而非 JOIN：articleTag 是多对多，JOIN 会让挂多个标签的文章出现多行，
        // 得再加 DISTINCT，而 DISTINCT 与分页 COUNT 一起用更容易出错；EXISTS 天然去重。
        // t.deleted = 0 必须手写：@TableLogic 只保护 MyBatis-Plus 生成的 SQL，手写片段
        // 它管不着 —— 与 CategoryMapper.selectCategoryWithArticleCount 是同一个坑。
        // {0} 是预编译占位符，不是字符串拼接。
        // 此处用 article.articleId 引用外层表，依赖 MP 生成不带别名的 FROM article；
        // 若将来升级 MP 后它改为生成别名，本片段会失效 —— 单测发现不了（单测断言的是
        // wrapper 里的字符串，不是数据库真跑的结果），由冒烟脚本第二层的 tagId 筛选兜住。
        wrapper.exists(query.getTagId() != null,
                "SELECT 1 FROM articleTag t WHERE t.articleId = article.articleId"
                        + " AND t.tagId = {0} AND t.deleted = 0", query.getTagId());
    }

    private List<ArticleListVO> toVoList(List<Article> records) {
        return records.stream().map(this::toVo).toList();
    }

    /**
     * 显式逐字段赋值，不用 BeanUtils.copyProperties —— Article 带着 content(LONGTEXT)
     * 与 deleted，直接拷是「碰巧对了」但不可读；显式赋值后谁改了字段一眼看得见（设计规格 §4）。
     */
    private ArticleListVO toVo(Article article) {
        ArticleListVO vo = new ArticleListVO();
        vo.setArticleId(article.getArticleId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCoverImage(article.getCoverImage());
        vo.setCreatedAt(article.getCreatedAt());
        // 建表前导入的历史数据可能为 NULL，不能直接拆箱
        vo.setViewCount(article.getViewCount() == null ? 0 : article.getViewCount());
        return vo;
    }

    /** trim 后为空则视为不传；非空则以 trim 后的值做 LIKE（设计规格 §3）。 */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return escapeLike(trimmed);
    }

    /**
     * 转义 LIKE 通配符。技术博客里搜 user_name / 100% / C++ 是家常便饭：不转义时
     * _ 匹配任意单字符、% 匹配任意串，搜一个下划线几乎命中全站文章。
     * 必须先转 \ 自身，否则会把刚写进去的转义符再转一次（1% → 1\% → 1\\%，转义失效）。
     * 依赖 MySQL 默认转义符 \ 且 NO_BACKSLASH_ESCAPES 未开启（设计规格 §4）。
     */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    /** null → 10；其余一律钳进 [1, 50]。注意 0 得到的是 1 而不是默认值 10（设计规格 §3）。 */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
    }
}
