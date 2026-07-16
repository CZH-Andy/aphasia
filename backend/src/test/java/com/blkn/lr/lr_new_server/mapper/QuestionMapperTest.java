package com.blkn.lr.lr_new_server.mapper;

import com.blkn.lr.lr_new_server.dto.models.question.QuestionDto;
import com.blkn.lr.lr_new_server.exception.ForbiddenException;
import com.blkn.lr.lr_new_server.models.question.Question;
import com.blkn.lr.lr_new_server.models.results.QuestionSnapshot;
import com.blkn.lr.lr_new_server.models.rules.question.HintRule;
import com.blkn.lr.lr_new_server.models.rules.question.QuestionEvalRule;
import com.blkn.lr.lr_new_server.services.MediaUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionMapperTest {
    private MediaUrlService mediaUrlService;
    private QuestionMapper mapper;

    @BeforeEach
    void setUp() {
        mediaUrlService = MediaUrlService.forTesting(
                "0123456789abcdef0123456789abcdef",
                3600,
                "https://api.example.com",
                Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC));
        mapper = new QuestionMapper(mediaUrlService);
    }

    @Test
    void toDtoShouldSignAllInternalQuestionMedia() {
        Question question = new Question();
        question.setAudioUrl("/audio/doctor-1/prompt.mp3");
        question.setImageUrl("/images/doctor-1/prompt.png");
        question.setTypeName("AudioQuestion");
        QuestionEvalRule rule = new QuestionEvalRule();
        rule.setHintRules(List.of(new HintRule(
                "提示", "/audio/doctor-1/hint.mp3", null, null,
                0, 1, 0, 1)));
        question.setEvalRule(rule);

        QuestionDto dto = mapper.toDto(question);

        assertTrue(dto.getAudioUrl().startsWith(
                "https://api.example.com/audio/doctor-1/prompt.mp3?expires="));
        assertTrue(dto.getImageUrl().contains("signature="));
        assertTrue(dto.getEvalRule().getHintRules().get(0).getHintAudioUrl().contains("signature="));
        assertEquals("/audio/doctor-1/prompt.mp3", question.getAudioUrl(),
                "映射输出不得污染持久化模型");
    }

    @Test
    void toModelAndSnapshotShouldStoreStableUnsignedPaths() {
        String signedImage = mediaUrlService.sign("/images/doctor-1/prompt.png");
        QuestionDto dto = new QuestionDto();
        dto.setTypeName("WritingQuestion");
        dto.setImageUrl(signedImage);

        Question model = mapper.toModel(dto, "doctor-1");
        QuestionSnapshot snapshot = mapper.toSnapshot(model);

        assertEquals("/images/doctor-1/prompt.png", model.getImageUrl());
        assertEquals("/images/doctor-1/prompt.png", snapshot.getImageUrl());
    }

    @Test
    void shouldPreserveExternalMediaUrls() {
        QuestionDto dto = new QuestionDto();
        dto.setTypeName("AudioQuestion");
        dto.setImageUrl("https://cdn.example.com/picture.png");

        Question model = mapper.toModel(dto, "doctor-1");

        assertEquals("https://cdn.example.com/picture.png", model.getImageUrl());
        assertEquals("https://cdn.example.com/picture.png", mapper.toDto(model).getImageUrl());
    }

    @Test
    void shouldRejectCrossOwnerMediaReferences() {
        QuestionDto dto = new QuestionDto();
        dto.setTypeName("AudioQuestion");
        dto.setImageUrl("/images/other-doctor/private.png");
        QuestionEvalRule rule = new QuestionEvalRule();
        rule.setHintRules(List.of(new HintRule(
                "提示", "/audio/other-doctor/private.mp3", null, null,
                0, 1, 0, 1)));

        assertThrows(ForbiddenException.class, () -> mapper.toModel(dto, "doctor-1"));

        dto.setImageUrl(null);
        dto.setEvalRule(rule);
        assertThrows(ForbiddenException.class, () -> mapper.toModel(dto, "doctor-1"));
    }
}
