package com.example.pdmadmin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.pdmadmin.entity.News;
import com.example.pdmadmin.exception.BusinessException;
import com.example.pdmadmin.mapper.NewsMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsPinServiceTests {
    @Mock
    private NewsMapper newsMapper;
    @InjectMocks
    private NewsPinService service;
    @Captor
    private ArgumentCaptor<Wrapper<News>> updateCaptor;

    @BeforeAll
    static void initTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "news-pin"), News.class);
    }

    @Test
    void pinUpdateRequiresApprovedAndPublishedNews() {
        when(newsMapper.update(isNull(), any())).thenReturn(1);

        service.setTopFlag(20L, 1);

        verify(newsMapper).update(isNull(), updateCaptor.capture());
        LambdaUpdateWrapper<News> update = (LambdaUpdateWrapper<News>) updateCaptor.getValue();
        String conditions = update.getSqlSegment();
        assertTrue(conditions.contains("id ="));
        assertTrue(conditions.contains("audit_status ="));
        assertTrue(conditions.contains("publish_status ="));
        assertTrue(update.getSqlSet().startsWith("top_flag="));
    }

    @Test
    void missingOrUnpublishedNewsCannotBePinned() {
        assertThrows(BusinessException.class, () -> service.setTopFlag(21L, 1));
    }

    @Test
    void repeatedUnpinSucceedsWhenFlagIsAlreadyCleared() {
        when(newsMapper.exists(any())).thenReturn(true);

        service.setTopFlag(20L, 0);

        verify(newsMapper).exists(any());
    }

    @Test
    void invalidTopFlagDoesNotQueryDatabase() {
        assertThrows(BusinessException.class, () -> service.setTopFlag(20L, 2));
        verifyNoInteractions(newsMapper);
    }
}
