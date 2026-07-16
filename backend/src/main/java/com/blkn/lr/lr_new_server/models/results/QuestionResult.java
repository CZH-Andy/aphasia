package com.blkn.lr.lr_new_server.models.results;

import com.blkn.lr.lr_new_server.models.rules.question.CommandActions;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionResult {
    String sourceQuestion;
    QuestionSnapshot sourceQuestionSnapshot;
    Double finalScore;
    Integer answerTime;
    Boolean isHinted;
    Map<String, String> extraResults;
    String typeName;
    String audioContent;
    List<Integer> choiceSelected;
    List<CommandActions> actions;
    List<Double> clickCoordinate;
    String writingContent;
}
