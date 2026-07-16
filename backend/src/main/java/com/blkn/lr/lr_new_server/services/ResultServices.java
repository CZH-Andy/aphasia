package com.blkn.lr.lr_new_server.services;

import com.blkn.lr.lr_new_server.dao.ExamDao;
import com.blkn.lr.lr_new_server.dao.ExamResultDao;
import com.blkn.lr.lr_new_server.dao.QuestionDao;
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
import com.blkn.lr.lr_new_server.models.results.QuestionResult;
import com.blkn.lr.lr_new_server.models.results.SubCategoryResult;
import com.blkn.lr.lr_new_server.models.rules.exam.DiagnosisRule;
import com.blkn.lr.lr_new_server.models.rules.question.CommandActions;
import com.blkn.lr.lr_new_server.models.rules.subcategory.TerminateRule;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResultServices {
    private static final int MAX_EXTRA_RESULT_TEXT_LENGTH = 20_000;
    private static final int MAX_EXTRA_RESULTS_TOTAL_LENGTH = 40_000;

    private final ExamResultDao resultDao;
    private final ExamDao examDao;
    private final QuestionDao questionDao;
    private final ExamResultMapper examResultMapper;
    private final QuestionMapper questionMapper;

    public List<ExamResultDto> getResultsByUserId(String ownerId, boolean isRecovery) {
        return resultDao.findByOwnerId(ownerId, isRecovery).stream()
                .map(examResultMapper::toDto)
                .toList();
    }

    public ExamResultDto saveResult(ExamResultDto resultDto, String ownerId) {
        if (resultDto.getId() == null || resultDto.getId().isBlank()) {
            return createResult(resultDto, ownerId);
        }
        return updateResult(resultDto, ownerId);
    }

    private ExamResultDto createResult(ExamResultDto resultDto, String ownerId) {
        Exam exam = requirePublishedExam(resultDto.getExamId());

        ExamResult result = new ExamResult();
        result.setOwnerId(ownerId);
        result.setExamId(exam.getId());
        result.setRevision(0L);
        result.setResultText(null);
        result.setFinalScore(0D);
        result.setStartTime(new Date());
        result.setFinishTime(null);
        result.setIsRecovery(exam.isRecovery());
        result.setIsDisabled(false);
        result.setExamName(exam.getName());
        result.setCategoryResults(buildEmptyResultTree(exam));

        ExamResult created = resultDao.insert(result);
        if (created == null) {
            throw new BusinessErrorException("创建作答记录失败");
        }
        return examResultMapper.toDto(created);
    }

    private ExamResultDto updateResult(ExamResultDto resultDto, String ownerId) {
        ExamResult existing = resultDao.findByIdWithOwnerId(ownerId, resultDto.getId());
        if (existing == null) {
            throw new NotFoundException("作答记录不存在");
        }

        long existingRevision = existing.getRevision() == null ? 0 : existing.getRevision();
        long requestRevision = resultDto.getRevision() == null ? 0 : resultDto.getRevision();
        if (requestRevision != existingRevision) {
            throw new ConflictException("作答记录已在其他请求中更新，请刷新后重试");
        }
        if (existing.getFinishTime() != null) {
            throw new ConflictException("已完成的作答记录不能再次修改");
        }

        String examId = existing.getExamId() == null ? resultDto.getExamId() : existing.getExamId();
        if (!Objects.equals(examId, resultDto.getExamId())) {
            throw new BusinessErrorException("作答记录所属套题不能修改");
        }
        Exam exam = requirePublishedExam(examId);
        Map<String, Question> questions = loadQuestions(exam);

        ExamResult updatedModel = new ExamResult();
        updatedModel.setId(existing.getId());
        updatedModel.setOwnerId(ownerId);
        updatedModel.setExamId(exam.getId());
        updatedModel.setRevision(existingRevision);
        updatedModel.setStartTime(existing.getStartTime());
        updatedModel.setFinishTime(resultDto.getFinishTime() == null ? null : new Date());
        updatedModel.setIsRecovery(exam.isRecovery());
        updatedModel.setIsDisabled(false);
        updatedModel.setExamName(exam.getName());
        updatedModel.setCategoryResults(canonicalizeResults(
                exam,
                existing.getCategoryResults(),
                resultDto.getCategoryResults(),
                questions));
        updatedModel.setFinalScore(sumCategoryScores(updatedModel.getCategoryResults()));

        if (updatedModel.getFinishTime() != null) {
            ensureAssessmentComplete(exam, updatedModel.getCategoryResults(), questions);
            updatedModel.setResultText(diagnose(exam, updatedModel.getCategoryResults()));
        } else {
            updatedModel.setResultText(null);
        }

        ExamResult updated = resultDao.updateOwned(updatedModel, existingRevision);
        if (updated == null) {
            throw new ConflictException("作答记录已在其他请求中更新，请刷新后重试");
        }
        return examResultMapper.toDto(updated);
    }

    private Exam requirePublishedExam(String examId) {
        if (examId == null || !ObjectId.isValid(examId)) {
            throw new NotFoundException("套题不存在");
        }
        Exam exam = examDao.findPublishedExamById(examId);
        if (exam == null) {
            throw new NotFoundException("套题不存在或尚未发布");
        }
        if (exam.getCategories() == null) {
            throw new BusinessErrorException("套题结构损坏：缺少亚项");
        }
        for (QuestionCategory category : exam.getCategories()) {
            if (category == null || category.getSubCategories() == null) {
                throw new BusinessErrorException("套题结构损坏：缺少子项");
            }
            for (QuestionSubCategory subCategory : category.getSubCategories()) {
                if (subCategory == null || subCategory.getQuestions() == null) {
                    throw new BusinessErrorException("套题结构损坏：缺少题目列表");
                }
            }
        }
        return exam;
    }

    private List<CategoryResult> buildEmptyResultTree(Exam exam) {
        List<CategoryResult> categories = new ArrayList<>();
        for (QuestionCategory category : exam.getCategories()) {
            CategoryResult categoryResult = new CategoryResult();
            categoryResult.setName(category.getDescription());
            categoryResult.setFinalScore(0D);
            List<SubCategoryResult> subResults = new ArrayList<>();
            for (QuestionSubCategory subCategory : category.getSubCategories()) {
                SubCategoryResult subResult = new SubCategoryResult();
                subResult.setName(subCategory.getDescription());
                subResult.setFinalScore(0D);
                subResult.setTerminateReason(null);
                subResult.setQuestionResults(new ArrayList<>());
                subResults.add(subResult);
            }
            categoryResult.setSubResults(subResults);
            categories.add(categoryResult);
        }
        return categories;
    }

    private Map<String, Question> loadQuestions(Exam exam) {
        Set<String> questionIds = new HashSet<>();
        for (QuestionCategory category : exam.getCategories()) {
            for (QuestionSubCategory subCategory : category.getSubCategories()) {
                questionIds.addAll(subCategory.getQuestions());
            }
        }

        Map<String, Question> questions = new HashMap<>();
        for (Question question : questionDao.findAllByIds(questionIds)) {
            questions.put(question.getId(), question);
        }
        if (questions.size() != questionIds.size()) {
            throw new BusinessErrorException("套题引用了已删除或不存在的题目，无法继续作答");
        }
        return questions;
    }

    private List<CategoryResult> canonicalizeResults(
            Exam exam,
            List<CategoryResult> existingCategories,
            List<CategoryResultDto> submittedCategories,
            Map<String, Question> questions) {
        if (submittedCategories == null || submittedCategories.size() != exam.getCategories().size()) {
            throw new BusinessErrorException("作答记录的亚项结构与套题不一致");
        }
        if (existingCategories == null || existingCategories.size() != exam.getCategories().size()) {
            throw new BusinessErrorException("已保存的作答记录结构损坏");
        }

        for (int categoryIndex = 0; categoryIndex < exam.getCategories().size(); categoryIndex++) {
            QuestionCategory category = exam.getCategories().get(categoryIndex);
            CategoryResult existingCategory = existingCategories.get(categoryIndex);
            CategoryResultDto submittedCategory = submittedCategories.get(categoryIndex);
            if (existingCategory == null
                    || existingCategory.getSubResults() == null
                    || submittedCategory == null
                    || submittedCategory.getSubResults() == null
                    || submittedCategory.getSubResults().size() != category.getSubCategories().size()
                    || existingCategory.getSubResults().size() != category.getSubCategories().size()) {
                throw new BusinessErrorException("作答记录的子项结构与套题不一致");
            }
            for (int subIndex = 0; subIndex < category.getSubCategories().size(); subIndex++) {
                SubCategoryResult existingSub = existingCategory.getSubResults().get(subIndex);
                SubCategoryResultDto submittedSub = submittedCategory.getSubResults().get(subIndex);
                if (existingSub == null
                        || existingSub.getQuestionResults() == null
                        || submittedSub == null
                        || submittedSub.getQuestionResults() == null) {
                    throw new BusinessErrorException("作答记录的题目结构损坏");
                }
            }
        }

        int[] nextAnswerPosition = findNextAnswerPosition(exam, existingCategories, questions);
        int appendedQuestionCount = 0;
        List<CategoryResult> canonicalCategories = new ArrayList<>();
        for (int categoryIndex = 0; categoryIndex < exam.getCategories().size(); categoryIndex++) {
            QuestionCategory category = exam.getCategories().get(categoryIndex);
            CategoryResult existingCategory = existingCategories.get(categoryIndex);
            CategoryResultDto submittedCategory = submittedCategories.get(categoryIndex);
            CategoryResult canonicalCategory = new CategoryResult();
            canonicalCategory.setName(category.getDescription());
            List<SubCategoryResult> canonicalSubResults = new ArrayList<>();

            for (int subIndex = 0; subIndex < category.getSubCategories().size(); subIndex++) {
                QuestionSubCategory subCategory = category.getSubCategories().get(subIndex);
                SubCategoryResult existingSub = existingCategory.getSubResults().get(subIndex);
                SubCategoryResultDto submittedSub = submittedCategory.getSubResults().get(subIndex);
                List<QuestionResult> existingQuestions = existingSub.getQuestionResults();
                List<QuestionResultDto> submittedQuestions = submittedSub.getQuestionResults();

                if (submittedQuestions.size() < existingQuestions.size()) {
                    throw new ConflictException("作答进度不能回退，请刷新后重试");
                }
                if (submittedQuestions.size() > subCategory.getQuestions().size()) {
                    throw new BusinessErrorException("作答题目数量超过套题定义");
                }

                SubCategoryResult canonicalSub = new SubCategoryResult();
                canonicalSub.setName(subCategory.getDescription());
                List<QuestionResult> canonicalQuestions = new ArrayList<>();

                for (int questionIndex = 0; questionIndex < submittedQuestions.size(); questionIndex++) {
                    String expectedQuestionId = subCategory.getQuestions().get(questionIndex);
                    Question question = questions.get(expectedQuestionId);
                    if (questionIndex < existingQuestions.size()) {
                        QuestionResult preserved = existingQuestions.get(questionIndex);
                        validateStoredQuestion(preserved, question, expectedQuestionId);
                        if (preserved.getSourceQuestionSnapshot() == null) {
                            preserved.setSourceQuestionSnapshot(questionMapper.toSnapshot(question));
                        }
                        canonicalQuestions.add(preserved);
                    } else {
                        if (categoryIndex != nextAnswerPosition[0] || subIndex != nextAnswerPosition[1]) {
                            throw new BusinessErrorException("只能按套题顺序提交下一道题");
                        }
                        QuestionResultDto submittedQuestion = submittedQuestions.get(questionIndex);
                        canonicalQuestions.add(canonicalizeNewQuestion(
                                submittedQuestion,
                                question,
                                expectedQuestionId));
                        appendedQuestionCount++;
                    }
                }

                canonicalSub.setQuestionResults(canonicalQuestions);
                canonicalSub.setTerminateReason(findTerminateReason(subCategory, canonicalQuestions, questions));
                canonicalSub.setFinalScore(sumQuestionScores(canonicalQuestions));
                canonicalSubResults.add(canonicalSub);
            }

            canonicalCategory.setSubResults(canonicalSubResults);
            canonicalCategory.setFinalScore(sumSubCategoryScores(canonicalSubResults));
            canonicalCategories.add(canonicalCategory);
        }

        if (appendedQuestionCount > 1) {
            throw new ConflictException("每次请求只能追加一道题的作答结果");
        }
        return canonicalCategories;
    }

    private QuestionResult canonicalizeNewQuestion(
            QuestionResultDto submitted,
            Question question,
            String expectedQuestionId) {
        if (submitted.getSourceQuestion() == null
                || !Objects.equals(expectedQuestionId, submitted.getSourceQuestion().getId())) {
            throw new BusinessErrorException("作答题目与套题顺序不一致");
        }
        String expectedResultType = question.getTypeName() + "Result";
        if (!Objects.equals(expectedResultType, submitted.getTypeName())) {
            throw new BusinessErrorException("作答结果类型与题目类型不一致");
        }
        validateScore(submitted.getFinalScore(), question);
        validateAnswerPayload(submitted, question);
        validateExtraResults(submitted.getExtraResults());

        QuestionResult result = examResultMapper.questionResultToModel(submitted);
        result.setSourceQuestion(expectedQuestionId);
        result.setSourceQuestionSnapshot(questionMapper.toSnapshot(question));
        result.setIsHinted(Boolean.TRUE.equals(submitted.getIsHinted()));
        result.setExtraResults(submitted.getExtraResults() == null
                ? Map.of()
                : new HashMap<>(submitted.getExtraResults()));
        retainOnlyExpectedPayload(result, submitted, question.getTypeName());
        return result;
    }

    private void validateStoredQuestion(QuestionResult result, Question question, String expectedQuestionId) {
        if (!Objects.equals(expectedQuestionId, result.getSourceQuestion())) {
            throw new BusinessErrorException("已保存的作答题目顺序损坏");
        }
        validateScore(result.getFinalScore(), question);
    }

    private void validateScore(Double score, Question question) {
        if (score == null || !Double.isFinite(score)) {
            throw new BusinessErrorException("题目得分必须是有限数值");
        }
        double fullScore = question.getEvalRule() == null || question.getEvalRule().getFullScore() == null
                ? 0
                : question.getEvalRule().getFullScore();
        if (!Double.isFinite(fullScore) || fullScore < 0) {
            throw new BusinessErrorException("题目满分配置无效");
        }
        if (score < 0 || score > fullScore) {
            throw new BusinessErrorException("题目得分超出0到满分的范围");
        }
    }

    private void validateAnswerPayload(QuestionResultDto result, Question question) {
        switch (question.getTypeName()) {
            case "AudioQuestion" -> {
                if (result.getAudioContent() == null) {
                    throw new BusinessErrorException("录音题缺少识别文本");
                }
            }
            case "ChoiceQuestion" -> {
                if (result.getChoiceSelected() == null) {
                    throw new BusinessErrorException("选择题缺少选项记录");
                }
                int choiceCount = question.getEvalRule().getChoices() == null
                        ? 0
                        : question.getEvalRule().getChoices().size();
                if (result.getChoiceSelected().stream().anyMatch(
                        index -> index == null || index < 0 || index >= choiceCount)
                        || new HashSet<>(result.getChoiceSelected()).size()
                        != result.getChoiceSelected().size()) {
                    throw new BusinessErrorException("选择题包含无效或重复的选项");
                }
            }
            case "CommandQuestion" -> {
                if (result.getActions() == null) {
                    throw new BusinessErrorException("指令题缺少动作记录");
                }
                validateActions(result.getActions(), question);
            }
            case "WritingQuestion" -> {
                if (result.getWritingContent() == null) {
                    throw new BusinessErrorException("书写题缺少识别文本");
                }
            }
            case "ItemFindingQuestion" -> validateCoordinate(result.getClickCoordinate());
            default -> throw new BusinessErrorException("不支持的题目类型");
        }
    }

    private void validateActions(List<CommandActions> actions, Question question) {
        int slotCount = question.getEvalRule().getSlots() == null
                ? 0
                : question.getEvalRule().getSlots().size();
        for (CommandActions action : actions) {
            if (action == null
                    || action.getSourceSlotIndex() == null
                    || action.getSourceSlotIndex() < 0
                    || action.getSourceSlotIndex() >= slotCount
                    || action.getFirstAction() == null) {
                throw new BusinessErrorException("指令题包含无效动作");
            }
            if (action.getTargetSlotIndex() != null
                    && (action.getTargetSlotIndex() < 0 || action.getTargetSlotIndex() >= slotCount)) {
                throw new BusinessErrorException("指令题包含无效目标槽位");
            }
            if ((action.getTargetSlotIndex() == null) != (action.getSecondAction() == null)) {
                throw new BusinessErrorException("指令题目标槽位与放置动作不完整");
            }
        }
    }

    private void retainOnlyExpectedPayload(
            QuestionResult stored,
            QuestionResultDto submitted,
            String questionType) {
        stored.setAudioContent(null);
        stored.setChoiceSelected(null);
        stored.setActions(null);
        stored.setClickCoordinate(null);
        stored.setWritingContent(null);

        switch (questionType) {
            case "AudioQuestion" -> stored.setAudioContent(submitted.getAudioContent());
            case "ChoiceQuestion" -> stored.setChoiceSelected(List.copyOf(submitted.getChoiceSelected()));
            case "CommandQuestion" -> stored.setActions(List.copyOf(submitted.getActions()));
            case "WritingQuestion" -> stored.setWritingContent(submitted.getWritingContent());
            case "ItemFindingQuestion" ->
                    stored.setClickCoordinate(List.copyOf(submitted.getClickCoordinate()));
            default -> throw new BusinessErrorException("不支持的题目类型");
        }
    }

    private void validateCoordinate(List<Double> coordinate) {
        if (coordinate == null || coordinate.size() != 2
                || coordinate.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new BusinessErrorException("寻物题坐标格式错误");
        }
        boolean noAnswer = coordinate.get(0) == -1D && coordinate.get(1) == -1D;
        boolean normalized = coordinate.stream().allMatch(value -> value >= 0D && value <= 1D);
        if (!noAnswer && !normalized) {
            throw new BusinessErrorException("寻物题坐标必须位于0到1之间");
        }
    }

    private void validateExtraResults(Map<String, String> extraResults) {
        if (extraResults == null) {
            return;
        }
        int totalLength = 0;
        for (Map.Entry<String, String> entry : extraResults.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || entry.getKey().length() > 200
                    || entry.getValue().length() > MAX_EXTRA_RESULT_TEXT_LENGTH) {
                throw new BusinessErrorException("作答详情字段过长或为空");
            }
            totalLength += entry.getKey().length() + entry.getValue().length();
        }
        if (totalLength > MAX_EXTRA_RESULTS_TOTAL_LENGTH) {
            throw new BusinessErrorException("作答详情总长度超过限制");
        }
    }

    private double sumQuestionScores(List<QuestionResult> results) {
        return results.stream().mapToDouble(QuestionResult::getFinalScore).sum();
    }

    private double sumSubCategoryScores(List<SubCategoryResult> results) {
        return results.stream().mapToDouble(SubCategoryResult::getFinalScore).sum();
    }

    private double sumCategoryScores(List<CategoryResult> results) {
        return results.stream().mapToDouble(CategoryResult::getFinalScore).sum();
    }

    private String findTerminateReason(
            QuestionSubCategory subCategory,
            List<QuestionResult> results,
            Map<String, Question> questions) {
        if (subCategory.getTerminateRules() == null) {
            return null;
        }
        for (TerminateRule rule : subCategory.getTerminateRules()) {
            if (!"ContinuousWrongAnswerTerminate".equals(rule.getTypeName())
                    || rule.getErrorCountThreshold() == null
                    || rule.getErrorCountThreshold() <= 0) {
                continue;
            }
            int consecutiveWrong = 0;
            for (int index = 0; index < results.size(); index++) {
                Question question = questions.get(subCategory.getQuestions().get(index));
                double fullScore = question.getEvalRule() == null
                        || question.getEvalRule().getFullScore() == null
                        ? 0
                        : question.getEvalRule().getFullScore();
                consecutiveWrong = results.get(index).getFinalScore() < fullScore / 2
                        ? consecutiveWrong + 1
                        : 0;
                if (consecutiveWrong >= rule.getErrorCountThreshold()) {
                    return rule.getReason();
                }
            }
        }
        return null;
    }

    private int[] findNextAnswerPosition(
            Exam exam,
            List<CategoryResult> existingCategories,
            Map<String, Question> questions) {
        for (int categoryIndex = 0; categoryIndex < exam.getCategories().size(); categoryIndex++) {
            QuestionCategory category = exam.getCategories().get(categoryIndex);
            for (int subIndex = 0; subIndex < category.getSubCategories().size(); subIndex++) {
                QuestionSubCategory subCategory = category.getSubCategories().get(subIndex);
                SubCategoryResult existing = existingCategories.get(categoryIndex).getSubResults().get(subIndex);
                boolean complete = existing.getQuestionResults().size() == subCategory.getQuestions().size();
                boolean terminated = findTerminateReason(
                        subCategory,
                        existing.getQuestionResults(),
                        questions) != null;
                if (!complete && !terminated) {
                    return new int[]{categoryIndex, subIndex};
                }
            }
        }
        return new int[]{-1, -1};
    }

    private void ensureAssessmentComplete(
            Exam exam,
            List<CategoryResult> categoryResults,
            Map<String, Question> questions) {
        for (int categoryIndex = 0; categoryIndex < exam.getCategories().size(); categoryIndex++) {
            QuestionCategory category = exam.getCategories().get(categoryIndex);
            for (int subIndex = 0; subIndex < category.getSubCategories().size(); subIndex++) {
                QuestionSubCategory subCategory = category.getSubCategories().get(subIndex);
                SubCategoryResult result = categoryResults.get(categoryIndex).getSubResults().get(subIndex);
                boolean allAnswered = result.getQuestionResults().size() == subCategory.getQuestions().size();
                boolean terminated = findTerminateReason(
                        subCategory,
                        result.getQuestionResults(),
                        questions) != null;
                if (!allAnswered && !terminated) {
                    throw new BusinessErrorException("仍有未完成的题目，不能结束作答");
                }
            }
        }
    }

    private String diagnose(Exam exam, List<CategoryResult> categoryResults) {
        String diagnosis = null;
        if (exam.getDiagnosisRules() == null) {
            return null;
        }
        for (DiagnosisRule rule : exam.getDiagnosisRules()) {
            if (!"DiagnoseByScoreRange".equals(rule.getTypeName())
                    || rule.getCategoryIndices() == null
                    || rule.getRanges() == null
                    || rule.getCategoryIndices().size() != rule.getRanges().size()) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < rule.getCategoryIndices().size(); index++) {
                int categoryIndex = rule.getCategoryIndices().get(index);
                if (categoryIndex < 0 || categoryIndex >= categoryResults.size()) {
                    matches = false;
                    break;
                }
                Map<String, Double> range = rule.getRanges().get(index);
                if (range == null) {
                    matches = false;
                    break;
                }
                Double min = range.get("min");
                Double max = range.get("max");
                double score = categoryResults.get(categoryIndex).getFinalScore();
                if (min == null || max == null || score < min || score > max) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                diagnosis = rule.getAphasiaType();
            }
        }
        return diagnosis;
    }

    public void deleteResult(String ownerId, String resultId) {
        resultDao.deleteByIdWithOwnerId(ownerId, resultId);
    }
}
