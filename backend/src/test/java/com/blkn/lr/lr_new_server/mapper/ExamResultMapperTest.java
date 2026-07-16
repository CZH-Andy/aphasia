package com.blkn.lr.lr_new_server.mapper;

import com.blkn.lr.lr_new_server.dao.QuestionDao;
import com.blkn.lr.lr_new_server.dto.models.result.ExamResultDto;
import com.blkn.lr.lr_new_server.dto.models.question.QuestionDto;
import com.blkn.lr.lr_new_server.dto.models.result.QuestionResultDto;
import com.blkn.lr.lr_new_server.models.question.Question;
import com.blkn.lr.lr_new_server.models.results.CategoryResult;
import com.blkn.lr.lr_new_server.models.results.ExamResult;
import com.blkn.lr.lr_new_server.models.results.QuestionResult;
import com.blkn.lr.lr_new_server.models.results.QuestionSnapshot;
import com.blkn.lr.lr_new_server.models.results.SubCategoryResult;
import com.blkn.lr.lr_new_server.services.MediaUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamResultMapperTest {

    private QuestionDao questionDao;
    private ExamResultMapper examResultMapper;

    @BeforeEach
    void setUp() {
        questionDao = mock(QuestionDao.class);
        MediaUrlService mediaUrlService = MediaUrlService.forTesting(
                "0123456789abcdef0123456789abcdef", 900,
                "http://localhost:8080", Clock.systemUTC());
        examResultMapper = new ExamResultMapper(questionDao, new QuestionMapper(mediaUrlService));
    }

    @Test
    void toDtoShouldBatchFetchAllQuestionsInOneCall() {
        Question q1 = question("q1", "题1");
        Question q2 = question("q2", "题2");
        when(questionDao.findAllByIds(any())).thenReturn(List.of(q1, q2));

        ExamResult result = resultWithQuestions(List.of("q1", "q2"));

        ExamResultDto dto = examResultMapper.toDto(result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(questionDao, times(1)).findAllByIds(captor.capture());
        verify(questionDao, never()).findById(any());
        assertTrue(captor.getValue().containsAll(List.of("q1", "q2")));

        var qResults = dto.getCategoryResults().get(0).getSubResults().get(0).getQuestionResults();
        assertEquals("题1", qResults.get(0).getSourceQuestion().getQuestionText());
        assertEquals("题2", qResults.get(1).getSourceQuestion().getQuestionText());
    }

    @Test
    void toDtoShouldUsePlaceholderForMissingQuestions() {
        when(questionDao.findAllByIds(any())).thenReturn(List.of());

        ExamResult result = resultWithQuestions(List.of("q-deleted"));

        ExamResultDto dto = examResultMapper.toDto(result);

        var sourceDto = dto.getCategoryResults().get(0).getSubResults().get(0)
                .getQuestionResults().get(0).getSourceQuestion();
        assertEquals("原问题已删除", sourceDto.getAlias());
    }

    @Test
    void toDtoShouldPreferArchivedSnapshotAndPreserveRawAnswer() {
        ExamResult result = resultWithQuestions(List.of("q-deleted"));
        QuestionResult stored = result.getCategoryResults().get(0).getSubResults().get(0)
                .getQuestionResults().get(0);
        stored.setSourceQuestionSnapshot(new QuestionSnapshot(
                "q-deleted", "历史题目", "历史题干", null, null, 10,
                "ChoiceQuestion", null));
        stored.setChoiceSelected(List.of(1, 2));

        ExamResultDto dto = examResultMapper.toDto(result);

        verify(questionDao).findAllByIds(List.of());
        QuestionResultDto question = dto.getCategoryResults().get(0).getSubResults().get(0)
                .getQuestionResults().get(0);
        assertEquals("历史题目", question.getSourceQuestion().getAlias());
        assertEquals(List.of(1, 2), question.getChoiceSelected());
    }

    @Test
    void toModelShouldPreserveExamIdentityRevisionAndRawPayload() {
        QuestionDto source = new QuestionDto();
        source.setId("q1");
        source.setTypeName("AudioQuestion");
        QuestionResultDto question = new QuestionResultDto();
        question.setSourceQuestion(source);
        question.setTypeName("AudioQuestionResult");
        question.setAudioContent("患者回答");

        var subDto = new com.blkn.lr.lr_new_server.dto.models.result.SubCategoryResultDto();
        subDto.setQuestionResults(List.of(question));
        var categoryDto = new com.blkn.lr.lr_new_server.dto.models.result.CategoryResultDto();
        categoryDto.setSubResults(List.of(subDto));
        ExamResultDto dto = new ExamResultDto();
        dto.setExamId("exam-1");
        dto.setRevision(7L);
        dto.setCategoryResults(List.of(categoryDto));

        ExamResult model = examResultMapper.toModel(dto, "patient-1");

        assertEquals("exam-1", model.getExamId());
        assertEquals(7L, model.getRevision());
        assertEquals("患者回答", model.getCategoryResults().get(0).getSubResults().get(0)
                .getQuestionResults().get(0).getAudioContent());
    }

    private Question question(String id, String text) {
        Question q = new Question();
        q.setId(id);
        q.setQuestionText(text);
        q.setTypeName("AudioQuestion");
        return q;
    }

    private ExamResult resultWithQuestions(List<String> questionIds) {
        ExamResult result = new ExamResult();
        result.setExamName("测试");
        result.setExamId("exam-1");
        result.setRevision(2L);

        LinkedList<QuestionResult> qrs = new LinkedList<>();
        for (String id : questionIds) {
            QuestionResult qr = new QuestionResult();
            qr.setSourceQuestion(id);
            qr.setTypeName("AudioQuestionResult");
            qrs.add(qr);
        }

        SubCategoryResult sub = new SubCategoryResult();
        sub.setName("子项");
        sub.setQuestionResults(qrs);

        CategoryResult cat = new CategoryResult();
        cat.setName("亚项");
        LinkedList<SubCategoryResult> subs = new LinkedList<>();
        subs.add(sub);
        cat.setSubResults(subs);

        LinkedList<CategoryResult> cats = new LinkedList<>();
        cats.add(cat);
        result.setCategoryResults(cats);
        return result;
    }
}
