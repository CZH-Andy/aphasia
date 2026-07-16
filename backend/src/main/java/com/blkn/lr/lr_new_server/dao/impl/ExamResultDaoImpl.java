package com.blkn.lr.lr_new_server.dao.impl;

import com.blkn.lr.lr_new_server.dao.ExamResultDao;
import com.blkn.lr.lr_new_server.models.results.ExamResult;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
@RequiredArgsConstructor
public class ExamResultDaoImpl implements ExamResultDao {
    private final MongoTemplate template;

    public ExamResult insert(ExamResult model) {
        return template.insert(model);
    }

    public ExamResult findByIdWithOwnerId(String ownerId, String resultId) {
        if (!ObjectId.isValid(resultId)) {
            return null;
        }
        return template.findOne(
                Query.query(where("_id").is(new ObjectId(resultId))
                        .and("ownerId").is(ownerId)
                        .and("isDisabled").is(false)),
                ExamResult.class);
    }

    public ExamResult updateOwned(ExamResult model, long expectedRevision) {
        if (!ObjectId.isValid(model.getId())) {
            return null;
        }

        Criteria revisionCriteria = expectedRevision == 0
                ? new Criteria().orOperator(
                        where("revision").is(0),
                        where("revision").is(null))
                : where("revision").is(expectedRevision);

        Query query = Query.query(new Criteria().andOperator(
                where("_id").is(new ObjectId(model.getId())),
                where("ownerId").is(model.getOwnerId()),
                where("isDisabled").is(false),
                revisionCriteria));

        Update update = new Update()
                .set("examId", model.getExamId())
                .set("revision", expectedRevision + 1)
                .set("examName", model.getExamName())
                .set("isRecovery", model.getIsRecovery())
                .set("resultText", model.getResultText())
                .set("finalScore", model.getFinalScore())
                .set("finishTime", model.getFinishTime())
                .set("categoryResults", model.getCategoryResults());

        return template.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ExamResult.class);
    }

    public List<ExamResult> findByOwnerId(String ownerId, boolean isRecovery) {
        return template.find(
                Query.query(where("ownerId").is(ownerId)
                        .and("isRecovery").is(isRecovery)
                        .and("isDisabled").is(false)),
                ExamResult.class);
    }

    public void deleteByIdWithOwnerId(String ownerId, String resultId) {
        if (!ObjectId.isValid(resultId)) {
            return;
        }
        template.update(ExamResult.class).matching(where("_id").is(new ObjectId(resultId)).and("ownerId").is(ownerId)).apply(new Update().set("isDisabled", true)).all().getModifiedCount();
    }
}
