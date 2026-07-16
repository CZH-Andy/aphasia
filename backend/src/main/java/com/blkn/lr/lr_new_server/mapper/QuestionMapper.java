package com.blkn.lr.lr_new_server.mapper;

import com.blkn.lr.lr_new_server.dto.models.question.QuestionDto;
import com.blkn.lr.lr_new_server.models.question.Question;
import com.blkn.lr.lr_new_server.models.results.QuestionSnapshot;
import com.blkn.lr.lr_new_server.services.MediaUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuestionMapper {
    private final MediaUrlService mediaUrlService;

    public QuestionDto toDto(Question q) {
        QuestionDto dto = new QuestionDto();
        if (q != null) {
            dto.setId(q.getId());
            dto.setAlias(q.getAlias());
            dto.setQuestionText(q.getQuestionText());
            dto.setAudioUrl(mediaUrlService.sign(q.getAudioUrl()));
            dto.setImageUrl(mediaUrlService.sign(q.getImageUrl()));
            dto.setOmitImageAfterSeconds(q.getOmitImageAfterSeconds());
            dto.setTypeName(q.getTypeName());
            dto.setEvalRule(mediaUrlService.signRule(q.getEvalRule()));
        } else {
            // 原题已被删除：返回占位 DTO（保留答题记录的可读性）
            dto.setAlias("原问题已删除");
            dto.setQuestionText("");
            dto.setOmitImageAfterSeconds(-1);
            dto.setTypeName("AudioQuestion");
        }
        return dto;
    }

    public Question toModel(QuestionDto dto, String ownerId) {
        Question model = new Question();
        if (dto.getId() != null) {
            model.setId(dto.getId());
        }
        model.setOwnerId(ownerId);
        model.setAlias(dto.getAlias());
        model.setQuestionText(dto.getQuestionText());
        model.setAudioUrl(mediaUrlService.normalizeOwnedForStorage(dto.getAudioUrl(), ownerId));
        model.setImageUrl(mediaUrlService.normalizeOwnedForStorage(dto.getImageUrl(), ownerId));
        model.setOmitImageAfterSeconds(dto.getOmitImageAfterSeconds());
        model.setTypeName(dto.getTypeName());
        model.setEvalRule(mediaUrlService.normalizeOwnedRuleForStorage(dto.getEvalRule(), ownerId));
        return model;
    }

    public QuestionSnapshot toSnapshot(Question question) {
        if (question == null) {
            return null;
        }
        return new QuestionSnapshot(
                question.getId(),
                question.getAlias(),
                question.getQuestionText(),
                mediaUrlService.normalizeForStorage(question.getAudioUrl()),
                mediaUrlService.normalizeForStorage(question.getImageUrl()),
                question.getOmitImageAfterSeconds(),
                question.getTypeName(),
                mediaUrlService.normalizeRuleForStorage(question.getEvalRule()));
    }

    public QuestionDto snapshotToDto(QuestionSnapshot snapshot) {
        if (snapshot == null) {
            return toDto(null);
        }
        QuestionDto dto = new QuestionDto();
        dto.setId(snapshot.getId());
        dto.setAlias(snapshot.getAlias());
        dto.setQuestionText(snapshot.getQuestionText());
        dto.setAudioUrl(mediaUrlService.sign(snapshot.getAudioUrl()));
        dto.setImageUrl(mediaUrlService.sign(snapshot.getImageUrl()));
        dto.setOmitImageAfterSeconds(snapshot.getOmitImageAfterSeconds());
        dto.setTypeName(snapshot.getTypeName());
        dto.setEvalRule(mediaUrlService.signRule(snapshot.getEvalRule()));
        return dto;
    }
}
