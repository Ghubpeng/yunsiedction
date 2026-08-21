package com.yunsie.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunsie.common.exception.BizException;
import com.yunsie.module.course.entity.Course;
import com.yunsie.module.course.entity.CourseChapter;
import com.yunsie.module.course.entity.CourseChapterPractice;
import com.yunsie.module.course.enums.CourseStatus;
import com.yunsie.module.course.error.CourseErrorCode;
import com.yunsie.module.course.mapper.CourseChapterMapper;
import com.yunsie.module.course.mapper.CourseChapterPracticeMapper;
import com.yunsie.module.course.mapper.CourseMapper;
import com.yunsie.module.course.vo.ChapterPracticeResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 章节练习完成记录（Stage 2.4）：
 * 判分由 question 域完成，本服务只记录服务端判分结果的汇总（答对/总数），
 * 服务端校验章节归属课程已发布；章节「掌握」= 视频全部完成 + 本记录存在。
 */
@Service
@RequiredArgsConstructor
public class ChapterPracticeServiceImpl {

    private final CourseChapterPracticeMapper practiceMapper;
    private final CourseChapterMapper chapterMapper;
    private final CourseMapper courseMapper;

    @Transactional(rollbackFor = Exception.class)
    public ChapterPracticeResultVO record(Long userId, Long chapterId, int correctCount, int totalCount) {
        if (correctCount > totalCount || totalCount <= 0) {
            throw new BizException(CourseErrorCode.CHAPTER_PRACTICE_INVALID);
        }
        CourseChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BizException(CourseErrorCode.CHAPTER_PRACTICE_INVALID);
        }
        Course course = courseMapper.selectById(chapter.getCourseId());
        if (course == null || CourseStatus.of(course.getStatus()) != CourseStatus.PUBLISHED) {
            throw new BizException(CourseErrorCode.CHAPTER_PRACTICE_INVALID);
        }
        int score = Math.round(100f * correctCount / totalCount);
        CourseChapterPractice record = practiceMapper.selectOne(new LambdaQueryWrapper<CourseChapterPractice>()
                .eq(CourseChapterPractice::getUserId, userId)
                .eq(CourseChapterPractice::getChapterId, chapterId));
        if (record == null) {
            record = new CourseChapterPractice();
            record.setUserId(userId);
            record.setChapterId(chapterId);
            record.setCorrectCount(correctCount);
            record.setTotalCount(totalCount);
            record.setScore(score);
            record.setFinished(1);
            record.setLastPracticeAt(LocalDateTime.now());
            practiceMapper.insert(record);
        } else {
            record.setCorrectCount(correctCount);
            record.setTotalCount(totalCount);
            record.setScore(score);
            record.setFinished(1);
            record.setLastPracticeAt(LocalDateTime.now());
            practiceMapper.updateById(record);
        }
        return new ChapterPracticeResultVO(chapterId, correctCount, totalCount, score, 1);
    }

    public List<CourseChapterPractice> listByUser(Long userId) {
        return practiceMapper.selectList(new LambdaQueryWrapper<CourseChapterPractice>()
                .eq(CourseChapterPractice::getUserId, userId));
    }
}
