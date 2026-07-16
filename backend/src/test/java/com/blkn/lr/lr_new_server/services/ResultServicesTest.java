package com.blkn.lr.lr_new_server.services;

import com.blkn.lr.lr_new_server.dao.ExamDao;
import com.blkn.lr.lr_new_server.dao.ExamResultDao;
import com.blkn.lr.lr_new_server.dao.QuestionDao;
import com.blkn.lr.lr_new_server.dto.models.question.QuestionDto;
import com.blkn.lr.lr_new_server.dto.models.result.CategoryResultDto;
import com.blkn.lr.lr_new_server.dto.models.result.ExamResultDto;
import com.blkn.lr.lr_new_server.dto.models.result.QuestionResultDto;
import com.blkn.lr.lr_new_server.dto.models.result.SubCategoryResultDto;
import com.blkn.lr.lr_new_server.exception.BusinessErrorException;
import com.blkn.lr.lr_new_server.exception.ConflictException;
import com.blkn.lr.lr_new_server.exception.NotFoundException;
import com.blkn.lr.lr_new_server.mapper.ExamResultMapper;
import com.blkn.lr.lr_new_server.mapper.QuestionMapper;
import com.blkn.lr.lr_new_server.models.exam.Exam;
import com.blkn.lr.lr_new_server.models.exam.QuestionCategory;
import com.blkn.lr.lr_new_server.models.exam.QuestionSubCategory;
import com.blkn.lr.lr_new_server.models.question.Question;
import com.blkn.lr.lr_new_server.models.results.CategoryResult;
import com.blkn.lr.lr_new_server.models.results.ExamResult;
import com.blkn.lr.lr_new_server.models.results.SubCategoryResult;
import com.blkn.lr.lr_new_server.models.rules.exam.DiagnosisRule;
import com.blkn.lr.lr_new_server.models.rules.question.Choice;
import com.blkn.lr.lr_new_server.models.rules.question.QuestionEvalRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultServicesTest {
    private static final String OWNER_ID = "patient-1";
    private static final String EXAM_ID = "507f1f77bcf86cd799439011";
    private static final String RESULT_ID = "507f1f77bcf86cd799439012";
    private static final String QUESTION_1_ID = "507f1f77bcf86cd799439013";
    private static final String QUESTION_2_ID = "507f1f77bcf86cd799439014";

    private ExamResultDao resultDao;
    private ExamDao examDao;
    private QuestionDao questionDao;
    private ResultServices service;

    @BeforeEach
    void setUp() {
        resultDao = mock(ExamResultDao.class);
        examDao = mock(ExamDao.class);
        questionDao = mock(QuestionDao.class);
        QuestionMapper questionMapper = new QuestionMapper(MediaUrlService.forTesting(
                "0123456789abcdef0123456789abcdef", 900,
                "http://localhost:8080", Clock.systemUTC()));
        ExamResultMapper resultMapper = new ExamResultMapper(questionDao, questionMapper);
        service = new ResultServices(resultDao, examDao, questionDao, resultMapper, questionMapper);
        when(questionDao.findAllByIds(any())).thenReturn(List.of());
    }

    @Test
    void getResultsByUserIdShouldMapResults() {
        ExamResult stored = emptyStoredResult(0);
        when(resultDao.findByOwnerId(OWNER_ID, false)).thenReturn(List.of(stored));

        List<ExamResultDto> results = service.getResultsByUserId(OWNER_ID, false);

        assertEquals(1, results.size());
        assertEquals(EXAM_ID, results.get(0).getExamId());
        verify(resultDao).findByOwnerId(OWNER_ID, false);
    }

    @Test
    void createShouldIgnoreClientMetadataAndBuildCanonicalTree() {
        Exam exam = exam(List.of(QUESTION_1_ID));
        when(examDao.findPublishedExamById(EXAM_ID)).thenReturn(exam);
        when(resultDao.insert(any())).thenAnswer(invocation -> {
            ExamResult value = invocation.getArgument(0);
            value.setId(RESULT_ID);
            return value;
        });

        ExamResultDto request = new ExamResultDto();
        request.setExamId(EXAM_ID);
        request.setExamName("伪造名称");
        request.setResultText("伪造诊断");
        request.setFinalScore(999D);
        request.setStartTime(new Date(0));
        request.setIsRecovery(true);
        request.setIsDisabled(true);
        request.setCategoryResults(List.of());

        ExamResultDto created = service.saveResult(request, OWNER_ID);

        ArgumentCaptor<ExamResult> captor = ArgumentCaptor.forClass(ExamResult.class);
        verify(resultDao).insert(captor.capture());
        ExamResult inserted = captor.getValue();
        assertEquals("权威套题", inserted.getExamName());
        assertFalse(inserted.getIsRecovery());
        assertFalse(inserted.getIsDisabled());
        assertEquals(0D, inserted.getFinalScore());
        assertNull(inserted.getResultText());
        assertEquals("权威亚项", inserted.getCategoryResults().get(0).getName());
        assertEquals("权威子项", inserted.getCategoryResults().get(0).getSubResults().get(0).getName());
        assertNotNull(inserted.getStartTime());
        assertEquals(RESULT_ID, created.getId());
        assertEquals(0L, created.getRevision());
    }

    @Test
    void updateShouldNotRevealOrOverwriteAnotherOwnersRecord() {
        when(resultDao.findByIdWithOwnerId(OWNER_ID, RESULT_ID)).thenReturn(null);
        ExamResultDto request = updateRequest(0, List.of(), false);

        assertThrows(NotFoundException.class, () -> service.saveResult(request, OWNER_ID));
        verify(resultDao, never()).updateOwned(any(), anyLong());
    }

    @Test
    void updateShouldRejectStaleRevision() {
        when(resultDao.findByIdWithOwnerId(OWNER_ID, RESULT_ID)).thenReturn(emptyStoredResult(2));
        ExamResultDto request = updateRequest(1, List.of(), false);

        assertThrows(ConflictException.class, () -> service.saveResult(request, OWNER_ID));
        verify(examDao, never()).findPublishedExamById(any());
    }

    @Test
    void updateShouldRejectFinishedRecord() {
        ExamResult existing = emptyStoredResult(2);
        existing.setFinishTime(new Date());
        when(resultDao.findByIdWithOwnerId(OWNER_ID, RESULT_ID)).thenReturn(existing);

        assertThrows(ConflictException.class,
                () -> service.saveResult(updateRequest(2, List.of(), false), OWNER_ID));
    }

    @Test
    void updateShouldArchiveRawAnswerAndRecalculateTotalsAndDiagnosis() {
        Exam exam = exam(List.of(QUESTION_1_ID));
        Question question = choiceQuestion(QUESTION_1_ID, 6D);
        ExamResult existing = emptyStoredResult(0);
        when(resultDao.findByIdWithOwnerId(OWNER_ID, RESULT_ID)).thenReturn(existing);
        when(examDao.findPublishedExamById(EXAM_ID)).thenReturn(exam);
        when(questionDao.findAllByIds(any())).thenReturn(List.of(question));
        when(resultDao.updateOwned(any(), eq(0L))).thenAnswer(invocation -> {
            ExamResult value = invocation.getArgument(0);
            value.setRevision(1L);
            return value;
        });

        QuestionResultDto answer = choiceAnswer(QUESTION_1_ID, 4D);
        ExamResultDto request = updateRequest(0, List.of(answer), true);
        request.setExamName("伪造名称");
        request.setFinalScore(999D);
        request.setResultText("伪造诊断");

        ExamResultDto saved = service.saveResult(request, OWNER_ID);

        ArgumentCaptor<ExamResult> captor = ArgumentCaptor.forClass(ExamResult.class);
        verify(resultDao).updateOwned(captor.capture(), eq(0L));
        ExamResult persisted = captor.getValue();
        var persistedQuestion = persisted.getCategoryResults().get(0).getSubResults().get(0)
                .getQuestionResults().get(0);
        assertEquals("权威套题", persisted.getExamName());
        assertEquals(4D, persisted.getFinalScore());
        assertEquals("服务端诊断", persisted.getResultText());
        assertNotNull(persisted.getFinishTime());
        assertEquals(List.of(0), persistedQuestion.getChoiceSelected());
        assertEquals(QUESTION_1_ID, persistedQuestion.getSourceQuestionSnapshot().getId());
        assertEquals("权威题目", persistedQuestion.getSourceQuestionSnapshot().getAlias());
        assertEquals(1L, saved.getRevision());
        assertEquals(4D, saved.getCategoryResults().get(0).getFinalScore());
        assertEquals("服务端诊断", saved.getResultText());
    }

    @Test
    void updateShouldRejectScoreAboveAuthoritativeFullScore() {
        Exam exam = exam(List.of(QUESTION_1_ID));
        when(resultDao.findByIdWithOwnerId(OWNER_ID, RESULT_ID)).thenReturn(emptyStoredResult(0));
        when(examDao.findPublishedExamById(EXAM_ID)).thenReturn(exam);
        when(questionDao.findAllByIds(any())).thenReturn(List.of(choiceQuestion(QUESTION_1_ID, 6D)));

        ExamResultDto request = updateRequest(0, List.of(choiceAnswer(QUESTION_1_ID, 7D)), false);

        assertThrows(BusinessErrorException.class, () -> service.saveResult(request, OWNER_ID));
        verify(resultDao, never()).updateOwned(any(), anyLong());
    }

    @Test
    void updateShouldRejectMoreThanOneNewAnswerPerRequest() {
        Exam exam = exam(List.of(QUESTION_1_ID, QUESTION_2_ID));
        when(resultDao.findByIdWithOwnerId(OWNER_ID, RESULT_ID)).thenReturn(emptyStoredResult(0));
        when(examDao.findPublishedExamById(EXAM_ID)).thenReturn(exam);
        when(questionDao.findAllByIds(any())).thenReturn(List.of(
                choiceQuestion(QUESTION_1_ID, 6D),
                choiceQuestion(QUESTION_2_ID, 6D)));

        ExamResultDto request = updateRequest(0, List.of(
                choiceAnswer(QUESTION_1_ID, 4D),
                choiceAnswer(QUESTION_2_ID, 4D)), false);

        assertThrows(ConflictException.class, () -> service.saveResult(request, OWNER_ID));
    }

    @Test
    void updateShouldRejectSkippingToLaterSubCategory() {
        Exam exam = exam(List.of(QUESTION_1_ID));
        QuestionSubCategory laterSub = new QuestionSubCategory();
        laterSub.setDescription("后续子项");
        laterSub.setQuestions(List.of(QUESTION_2_ID));
        laterSub.setTerminateRules(List.of());
        laterSub.setEvalRules(List.of());
        exam.getCategories().get(0).setSubCategories(List.of(
                exam.getCategories().get(0).getSubCategories().get(0),
                laterSub));

        ExamResult existing = emptyStoredResult(0);
        SubCategoryResult emptyLaterSub = new SubCategoryResult();
        emptyLaterSub.setName("后续子项");
        emptyLaterSub.setFinalScore(0D);
        emptyLaterSub.setQuestionResults(new ArrayList<>());
        existing.getCategoryResults().get(0).getSubResults().add(emptyLaterSub);

        when(resultDao.findByIdWithOwnerId(OWNER_ID, RESULT_ID)).thenReturn(existing);
        when(examDao.findPublishedExamById(EXAM_ID)).thenReturn(exam);
        when(questionDao.findAllByIds(any())).thenReturn(List.of(
                choiceQuestion(QUESTION_1_ID, 6D),
                choiceQuestion(QUESTION_2_ID, 6D)));

        SubCategoryResultDto first = new SubCategoryResultDto();
        first.setQuestionResults(List.of());
        SubCategoryResultDto second = new SubCategoryResultDto();
        second.setQuestionResults(List.of(choiceAnswer(QUESTION_2_ID, 4D)));
        CategoryResultDto category = new CategoryResultDto();
        category.setSubResults(List.of(first, second));
        ExamResultDto request = new ExamResultDto();
        request.setId(RESULT_ID);
        request.setExamId(EXAM_ID);
        request.setRevision(0L);
        request.setCategoryResults(List.of(category));

        assertThrows(BusinessErrorException.class, () -> service.saveResult(request, OWNER_ID));
        verify(resultDao, never()).updateOwned(any(), anyLong());
    }

    @Test
    void deleteResultShouldRemainOwnerScoped() {
        service.deleteResult(OWNER_ID, RESULT_ID);
        verify(resultDao).deleteByIdWithOwnerId(OWNER_ID, RESULT_ID);
    }

    private Exam exam(List<String> questionIds) {
        QuestionSubCategory subCategory = new QuestionSubCategory();
        subCategory.setDescription("权威子项");
        subCategory.setQuestions(questionIds);
        subCategory.setTerminateRules(List.of());
        subCategory.setEvalRules(List.of());

        QuestionCategory category = new QuestionCategory();
        category.setDescription("权威亚项");
        category.setSubCategories(List.of(subCategory));
        category.setRules(List.of());

        DiagnosisRule diagnosisRule = new DiagnosisRule();
        diagnosisRule.setTypeName("DiagnoseByScoreRange");
        diagnosisRule.setCategoryIndices(List.of(0));
        diagnosisRule.setRanges(List.of(Map.of("min", 4D, "max", 4D)));
        diagnosisRule.setAphasiaType("服务端诊断");

        Exam exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setName("权威套题");
        exam.setPublished(true);
        exam.setDisabled(false);
        exam.setRecovery(false);
        exam.setCategories(List.of(category));
        exam.setDiagnosisRules(List.of(diagnosisRule));
        exam.setRules(List.of());
        return exam;
    }

    private Question choiceQuestion(String id, double fullScore) {
        QuestionEvalRule rule = new QuestionEvalRule();
        rule.setTypeName("EvalChoiceQuestionByCorrectChoiceCount");
        rule.setFullScore(fullScore);
        rule.setChoices(List.of(new Choice(null, null, "选项")));

        Question question = new Question();
        question.setId(id);
        question.setAlias("权威题目");
        question.setTypeName("ChoiceQuestion");
        question.setEvalRule(rule);
        return question;
    }

    private ExamResult emptyStoredResult(long revision) {
        SubCategoryResult sub = new SubCategoryResult();
        sub.setName("权威子项");
        sub.setFinalScore(0D);
        sub.setQuestionResults(new ArrayList<>());

        CategoryResult category = new CategoryResult();
        category.setName("权威亚项");
        category.setFinalScore(0D);
        category.setSubResults(new ArrayList<>(List.of(sub)));

        ExamResult result = new ExamResult();
        result.setId(RESULT_ID);
        result.setOwnerId(OWNER_ID);
        result.setExamId(EXAM_ID);
        result.setRevision(revision);
        result.setExamName("权威套题");
        result.setStartTime(new Date());
        result.setIsRecovery(false);
        result.setIsDisabled(false);
        result.setCategoryResults(new ArrayList<>(List.of(category)));
        return result;
    }

    private ExamResultDto updateRequest(long revision, List<QuestionResultDto> answers, boolean finish) {
        SubCategoryResultDto sub = new SubCategoryResultDto();
        sub.setName("客户端子项");
        sub.setQuestionResults(answers);

        CategoryResultDto category = new CategoryResultDto();
        category.setName("客户端亚项");
        category.setSubResults(List.of(sub));

        ExamResultDto request = new ExamResultDto();
        request.setId(RESULT_ID);
        request.setExamId(EXAM_ID);
        request.setRevision(revision);
        request.setFinishTime(finish ? new Date(0) : null);
        request.setCategoryResults(List.of(category));
        return request;
    }

    private QuestionResultDto choiceAnswer(String questionId, double score) {
        QuestionDto source = new QuestionDto();
        source.setId(questionId);
        source.setTypeName("ChoiceQuestion");

        QuestionResultDto result = new QuestionResultDto();
        result.setSourceQuestion(source);
        result.setTypeName("ChoiceQuestionResult");
        result.setFinalScore(score);
        result.setIsHinted(false);
        result.setExtraResults(Map.of("患者选择的选项", "0"));
        result.setChoiceSelected(List.of(0));
        return result;
    }
}
