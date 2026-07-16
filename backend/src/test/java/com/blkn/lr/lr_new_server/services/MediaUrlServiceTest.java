package com.blkn.lr.lr_new_server.services;

import com.blkn.lr.lr_new_server.models.rules.question.Choice;
import com.blkn.lr.lr_new_server.exception.ForbiddenException;
import com.blkn.lr.lr_new_server.models.rules.question.HintRule;
import com.blkn.lr.lr_new_server.models.rules.question.ItemSlot;
import com.blkn.lr.lr_new_server.models.rules.question.QuestionEvalRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaUrlServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private MediaUrlService service;

    @BeforeEach
    void setUp() {
        service = MediaUrlService.forTesting(
                "0123456789abcdef0123456789abcdef",
                900,
                "https://api.example.com/",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldSignAndVerifyInternalMedia() {
        String signed = service.sign("/images/doctor-1/a.png");
        URI uri = URI.create(signed);
        Map<String, String> query = parseQuery(uri.getRawQuery());

        assertEquals("https://api.example.com/images/doctor-1/a.png", signed.substring(0, signed.indexOf('?')));
        assertEquals(String.valueOf(NOW.getEpochSecond() + 900), query.get("expires"));
        assertTrue(service.isValid(
                "images", "doctor-1", "a.png",
                Long.parseLong(query.get("expires")), query.get("signature")));
    }

    @Test
    void shouldRejectExpiredOrTamperedLinks() {
        URI uri = URI.create(service.sign("/audio/doctor-1/a.mp3"));
        Map<String, String> query = parseQuery(uri.getRawQuery());
        long expires = Long.parseLong(query.get("expires"));
        String signature = query.get("signature");

        assertFalse(service.isValid("audio", "doctor-1", "other.mp3", expires, signature));
        assertFalse(service.isValid("audio", "doctor-1", "a.mp3", expires, signature + "x"));
        assertFalse(service.isValid(
                "audio", "doctor-1", "a.mp3", NOW.getEpochSecond() - 1, signature));
    }

    @Test
    void shouldNormalizeLegacyAbsoluteAndPreviouslySignedUrls() {
        String legacy = "http://old-host:8080/images/doctor-1/a.png?expires=1&signature=old";

        assertEquals("/images/doctor-1/a.png", service.normalizeForStorage(legacy));
        assertNotEquals(legacy, service.sign(legacy));
        assertEquals("/images/doctor-1/a.png",
                service.normalizeOwnedForStorage(legacy, "doctor-1"));
        assertThrows(ForbiddenException.class,
                () -> service.normalizeOwnedForStorage(legacy, "doctor-2"));
    }

    @Test
    void shouldLeaveExternalAndAssetUrlsUntouched() {
        assertEquals("https://cdn.example.com/picture.png",
                service.sign("https://cdn.example.com/picture.png"));
        assertEquals("assets/images/picture.png",
                service.normalizeForStorage("assets/images/picture.png"));
        assertEquals(null, service.sign(null));
    }

    @Test
    void shouldTransformNestedRuleMediaWithoutMutatingStoredRule() {
        QuestionEvalRule rule = new QuestionEvalRule();
        rule.setImageUrl("/images/doctor-1/scene.png");
        rule.setHintRules(List.of(new HintRule(
                "提示", "/audio/doctor-1/hint.mp3", "/images/doctor-1/hint.png",
                null, 0, 1, 0, 1)));
        rule.setChoices(List.of(new Choice("/images/doctor-1/choice.png", null, "选项")));
        rule.setSlots(List.of(new ItemSlot("梳子", "/images/doctor-1/comb.png", null)));

        QuestionEvalRule signed = service.signRule(rule);

        assertTrue(signed.getImageUrl().contains("signature="));
        assertTrue(signed.getHintRules().get(0).getHintAudioUrl().contains("signature="));
        assertTrue(signed.getChoices().get(0).getImageUrl().contains("signature="));
        assertTrue(signed.getSlots().get(0).getItemImageUrl().contains("signature="));
        assertEquals("/images/doctor-1/scene.png", rule.getImageUrl());

        QuestionEvalRule normalized = service.normalizeRuleForStorage(signed);
        assertEquals("/images/doctor-1/scene.png", normalized.getImageUrl());
        assertEquals("/audio/doctor-1/hint.mp3", normalized.getHintRules().get(0).getHintAudioUrl());
    }

    @Test
    void shouldRejectUnsafeTtlConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> MediaUrlService.forTesting(
                "0123456789abcdef0123456789abcdef",
                0, "https://api.example.com", Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> MediaUrlService.forTesting(
                "0123456789abcdef0123456789abcdef",
                86401, "https://api.example.com", Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> MediaUrlService.forTesting(
                "too-short", 900, "https://api.example.com", Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> MediaUrlService.forTesting(
                "0123456789abcdef0123456789abcdef",
                900, "api.example.com", Clock.systemUTC()));
    }

    private Map<String, String> parseQuery(String rawQuery) {
        return java.util.Arrays.stream(rawQuery.split("&"))
                .map(item -> item.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(item -> item[0], item -> item[1]));
    }
}
