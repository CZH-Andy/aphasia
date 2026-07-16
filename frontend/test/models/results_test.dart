import 'dart:convert';

import 'package:aphasia_recovery/enum/command_actions.dart';
import 'package:aphasia_recovery/models/exam/exam_recovery.dart';
import 'package:aphasia_recovery/models/question/question.dart';
import 'package:aphasia_recovery/models/result/results.dart';
import 'package:aphasia_recovery/models/rules.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test("SubCategoryResult toJson test", () {
    var subCateRes = SubCategoryResult(finalScore: 1);
    subCateRes.questionResults.add(AudioQuestionResult(
        sourceQuestion: AudioQuestion(
            alias: "测试", questionText: "测试题干", imageUrl: "fake://image"),
        audioContent: "测试"));

    var subCateDecoded =
        SubCategoryResult.fromJson(jsonDecode(jsonEncode(subCateRes.toJson())));
    var qResDecoded = subCateDecoded.questionResults[0] as AudioQuestionResult;
    var qRes = subCateRes.questionResults[0] as AudioQuestionResult;
    expect(subCateDecoded.finalScore, subCateRes.finalScore);
    expect(qResDecoded.sourceQuestion.alias, qRes.sourceQuestion.alias);
    expect(qResDecoded.finalScore, qRes.finalScore);
    expect(qResDecoded.sourceQuestion.imageUrl, qRes.sourceQuestion.imageUrl);
    expect(qResDecoded.sourceQuestion.questionText,
        qRes.sourceQuestion.questionText);
    expect(qResDecoded.audioContent, qRes.audioContent);
  });

  test("ExamResult archives examId and optimistic revision", () {
    final result = ExamResult(
      examId: "exam-1",
      revision: 3,
      examName: "测试套题",
    );

    final decoded =
        ExamResult.fromJson(jsonDecode(jsonEncode(result.toJson())));

    expect(decoded.examId, "exam-1");
    expect(decoded.revision, 3);
  });

  test("creating a result requires a persisted exam id", () async {
    final exam = ExamQuestionSet(name: "未保存套题");

    await expectLater(
      ExamResult.createExamResult(exam: exam, isRecovery: false),
      throwsArgumentError,
    );
  });

  test("all raw answer payloads survive json round trip", () {
    final audio = AudioQuestionResult(
      sourceQuestion: AudioQuestion(alias: "录音"),
      audioContent: "患者语音识别文本",
    );
    final choice = ChoiceQuestionResult(
      sourceQuestion: ChoiceQuestion(alias: "选择"),
      choiceSelected: [0, 2],
    );
    final command = CommandQuestionResult(
      sourceQuestion: CommandQuestion(alias: "指令"),
      actions: [
        CommandActions(sourceSlotIndex: 1, firstAction: ClickAction.touch),
      ],
    );
    final writing = WritingQuestionResult(
      sourceQuestion: WritingQuestion(alias: "书写"),
      writingContent: "患者书写识别文本",
    );
    final item = ItemFindingQuestionResult(
      sourceQuestion: ItemFindingQuestion(alias: "寻物"),
      coordinate: [0.25, 0.75],
    );

    final values = [audio, choice, command, writing, item]
        .map((value) => QuestionResult.fromJson(
            jsonDecode(jsonEncode(value.toJson())) as Map<String, dynamic>))
        .toList();

    expect((values[0] as AudioQuestionResult).audioContent, "患者语音识别文本");
    expect((values[1] as ChoiceQuestionResult).choiceSelected, [0, 2]);
    expect((values[2] as CommandQuestionResult).actions.single.firstAction,
        ClickAction.touch);
    expect((values[3] as WritingQuestionResult).writingContent, "患者书写识别文本");
    expect(
        (values[4] as ItemFindingQuestionResult).clickCoordinate, [0.25, 0.75]);
  });
}
