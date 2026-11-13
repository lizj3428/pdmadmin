package com.example.pdmadmin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.entity.NewsTag;
import com.example.pdmadmin.entity.Tag;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.NewsReadRecordMapper;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudentNewsService {

    private final NewsMapper newsMapper;
    private final NewsReadRecordMapper newsReadRecordMapper;
    private final NewsTagMapper newsTagMapper;
    private final TagMapper tagMapper;

    public StudentNewsService(NewsMapper newsMapper, NewsReadRecordMapper newsReadRecordMapper,
                              NewsTagMapper newsTagMapper, TagMapper tagMapper) {
        this.newsMapper = newsMapper;
        this.newsReadRecordMapper = newsReadRecordMapper;
        this.newsTagMapper = newsTagMapper;
        this.tagMapper = tagMapper;
    }

    @Transactional
    public News readPublishedNews(Long studentId, Long newsId) {
        News news = newsMapper.selectOne(new LambdaQueryWrapper<News>()
                .eq(News::getId, newsId)
                .last("FOR UPDATE"));
        if (news == null || !Integer.valueOf(1).equals(news.getAuditStatus())
                || !Integer.valueOf(1).equals(news.getPublishStatus())) {
            throw new RuntimeException("资讯不存在或未发布！");
        }
        // 有效期过了就当已下线：expire_time 为空是长期有效
        if (news.getExpireTime() != null && news.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("该资讯已过期");
        }

        if (newsReadRecordMapper.count(studentId, newsId) == 0) {
            newsReadRecordMapper.insert(studentId, newsId);
            int updated = newsMapper.update(null, new LambdaUpdateWrapper<News>()
                    .eq(News::getId, newsId)
                    .setSql("view_count = COALESCE(view_count, 0) + 1"));
            if (updated != 1) {
                throw new IllegalStateException("阅读量更新失败！");
            }
            news.setViewCount(news.getViewCount() == null ? 1 : news.getViewCount() + 1);
        }

        List<Long> tagIds = newsTagMapper.selectList(new LambdaQueryWrapper<NewsTag>()
                        .eq(NewsTag::getNewsId, newsId))
                .stream().map(NewsTag::getTagId).toList();
        news.setTagNames(tagIds.isEmpty() ? List.of()
                : tagMapper.selectByIds(tagIds).stream().map(Tag::getName).toList());
        return news;
    }
}
