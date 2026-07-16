package com.blkn.lr.lr_new_server.dao.impl;

import com.blkn.lr.lr_new_server.models.results.ExamResult;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.ExecutableUpdate;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.TerminatingUpdate;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation.UpdateWithUpdate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExamResultDaoImpl 单元测试。
 *
 * <p>关键关注 {@link ExamResultDaoImpl#deleteByIdWithOwnerId}：软删除——
 * 不真正删 doc 而是 set isDisabled=true，且必须同时匹配 _id + ownerId，
 * 防止越权（患者 A 拿患者 B 的 resultId 调用也不能改 B 的数据）。
 */
class ExamResultDaoImplTest {

    private static final String OWNER_ID = "patient-1";
    private static final String RESULT_ID = "507f1f77bcf86cd799439011";

    private MongoTemplate template;
    private ExamResultDaoImpl dao;

    @BeforeEach
    void setUp() {
        template = mock(MongoTemplate.class);
        dao = new ExamResultDaoImpl(template);
    }

    @Test
    void insertShouldPassThroughTemplate() {
        ExamResult r = new ExamResult();
        when(template.insert(r)).thenReturn(r);
        assertSame(r, dao.insert(r));
    }

    @Test
    void findByIdWithOwnerIdShouldMatchOwnerAndExcludeDeletedRecords() {
        ExamResult r = new ExamResult();
        when(template.findOne(any(Query.class), eq(ExamResult.class))).thenReturn(r);

        assertSame(r, dao.findByIdWithOwnerId(OWNER_ID, RESULT_ID));

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).findOne(captor.capture(), eq(ExamResult.class));
        String query = captor.getValue().getQueryObject().toString();
        assertTrue(query.contains("_id"), query);
        assertTrue(query.contains("ownerId"), query);
        assertTrue(query.contains(OWNER_ID), query);
        assertTrue(query.contains("isDisabled"), query);
    }

    @Test
    void findByIdWithOwnerIdShouldIgnoreInvalidObjectId() {
        assertEquals(null, dao.findByIdWithOwnerId(OWNER_ID, "invalid"));
        verify(template, never()).findOne(any(Query.class), eq(ExamResult.class));
    }

    @Test
    void updateOwnedShouldUseOwnerRevisionAndOnlyMutableFields() {
        ExamResult model = new ExamResult();
        model.setId(RESULT_ID);
        model.setOwnerId(OWNER_ID);
        model.setExamId("exam-1");
        model.setResultText("诊断");
        model.setFinalScore(8D);
        model.setCategoryResults(List.of());
        when(template.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(ExamResult.class))).thenReturn(model);

        assertSame(model, dao.updateOwned(model, 3));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(template).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(ExamResult.class));

        String query = queryCaptor.getValue().getQueryObject().toString();
        assertTrue(query.contains("_id"), query);
        assertTrue(query.contains("ownerId"), query);
        assertTrue(query.contains("revision"), query);
        assertTrue(query.contains("3"), query);
        assertTrue(query.contains("isDisabled"), query);

        String update = updateCaptor.getValue().getUpdateObject().toString();
        assertTrue(update.contains("revision") && update.contains("4"), update);
        assertTrue(update.contains("categoryResults"), update);
        assertTrue(update.contains("finalScore"), update);
        assertFalse(update.contains("ownerId"), update);
        assertFalse(update.contains("startTime"), update);
        assertFalse(update.contains("isDisabled"), update);
    }

    @Test
    void updateOwnedRevisionZeroShouldAlsoMatchLegacyMissingRevision() {
        ExamResult model = new ExamResult();
        model.setId(RESULT_ID);
        model.setOwnerId(OWNER_ID);
        model.setCategoryResults(List.of());
        when(template.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(ExamResult.class))).thenReturn(model);

        dao.updateOwned(model, 0);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).findAndModify(
                captor.capture(),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(ExamResult.class));
        String query = captor.getValue().getQueryObject().toString();
        assertTrue(query.contains("$or"), query);
        assertTrue(query.contains("revision"), query);
    }

    @Test
    void findByOwnerIdShouldBuildQueryWithOwnerAndRecoveryAndIsDisabledFalse() {
        ExamResult r1 = new ExamResult();
        when(template.find(any(Query.class), eq(ExamResult.class))).thenReturn(List.of(r1));

        List<ExamResult> result = dao.findByOwnerId(OWNER_ID, false);
        assertEquals(List.of(r1), result);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(captor.capture(), eq(ExamResult.class));
        String queryStr = captor.getValue().getQueryObject().toString();
        // 关键：必须同时锁 ownerId + isRecovery + isDisabled=false（已删除的不再返）
        assertTrue(queryStr.contains(OWNER_ID), queryStr);
        assertTrue(queryStr.contains("isRecovery"), queryStr);
        assertTrue(queryStr.contains("isDisabled"), queryStr);
    }

    @Test
    void findByOwnerIdShouldPassRecoveryFlagTrueThrough() {
        when(template.find(any(Query.class), eq(ExamResult.class))).thenReturn(List.of());

        dao.findByOwnerId(OWNER_ID, true);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(captor.capture(), eq(ExamResult.class));
        // 序列化结果应包含 isRecovery: true
        String queryStr = captor.getValue().getQueryObject().toString();
        assertTrue(queryStr.contains("isRecovery") && queryStr.contains("true"), queryStr);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deleteByIdWithOwnerIdShouldSoftDeleteByMatchingBothIdAndOwnerId() {
        // 越权防护核心：matching 必须 AND _id + ownerId，否则越权改别人的 result。
        ExecutableUpdate<ExamResult> exec = mock(ExecutableUpdate.class);
        UpdateWithUpdate<ExamResult> withUpdate = mock(UpdateWithUpdate.class);
        TerminatingUpdate<ExamResult> terminating = mock(TerminatingUpdate.class);
        UpdateResult updateResult = mock(UpdateResult.class);

        when(template.update(ExamResult.class)).thenReturn(exec);
        when(exec.matching(any(CriteriaDefinition.class))).thenReturn(withUpdate);
        when(withUpdate.apply(any(Update.class))).thenReturn(terminating);
        when(terminating.all()).thenReturn(updateResult);
        when(updateResult.getModifiedCount()).thenReturn(1L);

        dao.deleteByIdWithOwnerId(OWNER_ID, RESULT_ID);

        // 抓 matching 入参（CriteriaDefinition）—— 必须含 _id 和 ownerId 两个条件
        ArgumentCaptor<CriteriaDefinition> criteriaCaptor = ArgumentCaptor.forClass(CriteriaDefinition.class);
        verify(exec).matching(criteriaCaptor.capture());
        String criteriaStr = criteriaCaptor.getValue().getCriteriaObject().toString();
        assertTrue(criteriaStr.contains("_id"), "must scope by _id: " + criteriaStr);
        assertTrue(criteriaStr.contains("ownerId"), "must AND ownerId for cross-tenant safety: " + criteriaStr);
        assertTrue(criteriaStr.contains(OWNER_ID), criteriaStr);

        // 抓 apply 入参（Update）—— 必须是 set isDisabled = true（软删除）
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(withUpdate).apply(updateCaptor.capture());
        String updateStr = updateCaptor.getValue().getUpdateObject().toString();
        assertTrue(updateStr.contains("isDisabled"), "must soft-delete: " + updateStr);
    }

    @Test
    void deleteByIdWithOwnerIdShouldIgnoreInvalidObjectId() {
        dao.deleteByIdWithOwnerId(OWNER_ID, "invalid");
        verify(template, never()).update(ExamResult.class);
    }
}
