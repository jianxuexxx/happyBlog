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

    /** 五个筛选参数。任务 2 实现。 */
    private void buildFilters(LambdaQueryWrapper<Article> wrapper, ArticleQueryDTO query) {
        // 任务 2 填充
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
