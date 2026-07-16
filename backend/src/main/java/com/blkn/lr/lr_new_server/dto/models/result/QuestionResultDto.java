package com.blkn.lr.lr_new_server.dto.models.result;

import com.blkn.lr.lr_new_server.dto.models.question.QuestionDto;
import com.blkn.lr.lr_new_server.models.rules.question.CommandActions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestionResultDto {
    @NotNull(message = "sourceQuestion不能为null")
    @Valid
    QuestionDto sourceQuestion;
    Double finalScore;

    @Min(value = 0, message = "answerTime不能为负数")
    @Max(value = 86400, message = "answerTime不能超过一天")
    Integer answerTime;

    Boolean isHinted;

    @Size(max = 50, message = "extraResults最多包含50项")
    Map<String, String> extraResults;

    String typeName;

    @Size(max = 20000, message = "audioContent过长")
    String audioContent;

    @Size(max = 100, message = "choiceSelected过长")
    List<Integer> choiceSelected;

    @Size(max = 200, message = "actions过长")
    List<CommandActions> actions;

    @Size(max = 2, message = "clickCoordinate必须是二维坐标")
    List<Double> clickCoordinate;

    @Size(max = 20000, message = "writingContent过长")
    String writingContent;
}
