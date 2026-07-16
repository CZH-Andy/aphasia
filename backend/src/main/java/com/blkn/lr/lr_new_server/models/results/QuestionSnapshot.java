package com.blkn.lr.lr_new_server.models.results;

import com.blkn.lr.lr_new_server.models.rules.question.QuestionEvalRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作答时的题目快照。
 *
 * <p>历史记录不能只引用可变、可删除的 question 文档，否则题目被删除后记录会失去题干、
 * 评分规则和媒体信息。快照不保存医生 ownerId，只保存解释该次作答所需的字段。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionSnapshot {
    String id;
    String alias;
    String questionText;
    String audioUrl;
    String imageUrl;
    int omitImageAfterSeconds;
    String typeName;
    QuestionEvalRule evalRule;
}
