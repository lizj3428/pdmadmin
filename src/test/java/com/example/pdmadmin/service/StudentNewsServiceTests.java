package com.example.pdmadmin.service;

import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.mapper.NewsMapper;
import com.example.pdmadmin.mapper.NewsReadRecordMapper;
import com.example.pdmadmin.mapper.NewsTagMapper;
import com.example.pdmadmin.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentNewsServiceTests {

    private static final Long STUDENT_ID = 7L;
    private static final Long NEWS_ID = 13L;

    @Mock
    private NewsMapper newsMapper;
    @Mock
    private NewsReadRecordMapper newsReadRecordMapper;
    @Mock
    private NewsTagMapper newsTagMapper;
    @Mock
    private TagMapper tagMapper;
    @InjectMocks
    private StudentNewsService studentNewsService;

    @Test
    void firstReadIncrementsTheExistingCount() {
        when(newsMapper.selectOne(any())).thenReturn(publishedNews(12));
        when(newsMapper.update(isNull(), any())).thenReturn(1);

        News result = studentNewsService.readPublishedNews(STUDENT_ID, NEWS_ID);

        assertEquals(13, result.getViewCount());
        verify(newsReadRecordMapper).insert(STUDENT_ID, NEWS_ID);
        verify(newsMapper).update(isNull(), any());
    }

    @Test
    void repeatReadKeepsTheExistingCount() {
        when(newsMapper.selectOne(any())).thenReturn(publishedNews(13));
        when(newsReadRecordMapper.count(STUDENT_ID, NEWS_ID)).thenReturn(1);

        News result = studentNewsService.readPublishedNews(STUDENT_ID, NEWS_ID);

        assertEquals(13, result.getViewCount());
        verify(newsReadRecordMapper, never()).insert(any(), any());
        verify(newsMapper, never()).update(any(), any());
    }

    @Test
    void unpublishedNewsDoesNotRecordARead() {
        News news = publishedNews(12);
        news.setPublishStatus(2);
        when(newsMapper.selectOne(any())).thenReturn(news);

        assertThrows(RuntimeException.class, () -> studentNewsService.readPublishedNews(STUDENT_ID, NEWS_ID));

        verifyNoInteractions(newsReadRecordMapper);
        verify(newsMapper, never()).update(any(), any());
    }

    @Test
    void failedIncrementPropagatesAnErrorForRollback() {
        when(newsMapper.selectOne(any())).thenReturn(publishedNews(12));

        assertThrows(IllegalStateException.class,
                () -> studentNewsService.readPublishedNews(STUDENT_ID, NEWS_ID));
    }

    private News publishedNews(int count) {
        News news = new News();
        news.setId(NEWS_ID);
        news.setAuditStatus(1);
        news.setPublishStatus(1);
        news.setViewCount(count);
        return news;
    }
}
