package com.blkn.lr.lr_new_server.services;

import com.blkn.lr.lr_new_server.config.AppSetting;
import com.blkn.lr.lr_new_server.config.StaticResourcesConfig;
import com.blkn.lr.lr_new_server.exception.ForbiddenException;
import com.blkn.lr.lr_new_server.models.rules.question.Choice;
import com.blkn.lr.lr_new_server.models.rules.question.HintRule;
import com.blkn.lr.lr_new_server.models.rules.question.ItemSlot;
import com.blkn.lr.lr_new_server.models.rules.question.QuestionEvalRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 为内部上传媒体生成短期 HMAC URL，并把持久化值规范化为无主机、无签名的相对路径。
 */
@Service
public class MediaUrlService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] signingSecret;
    private final long ttlSeconds;
    private final String publicBaseUrl;
    private final Clock clock;

    @Autowired
    public MediaUrlService(
            @Value("${media.signing.secret:${jwt.secret}}") String signingSecret,
            @Value("${media.url.ttl-seconds:3600}") long ttlSeconds,
            AppSetting appSetting,
            Environment environment) {
        this(signingSecret, ttlSeconds,
                appSetting.resolvePublicBaseUrl(environment.getProperty("server.port", "8080")),
                Clock.systemUTC());
    }

    private MediaUrlService(String signingSecret, long ttlSeconds, String publicBaseUrl, Clock clock) {
        if (signingSecret == null || signingSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("媒体签名密钥至少需要 32 字节");
        }
        if (ttlSeconds <= 0 || ttlSeconds > 86400) {
            throw new IllegalArgumentException("media.url.ttl-seconds 必须在 1 到 86400 之间");
        }
        this.signingSecret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.clock = clock;
    }

    public static MediaUrlService forTesting(
            String signingSecret,
            long ttlSeconds,
            String publicBaseUrl,
            Clock clock) {
        return new MediaUrlService(signingSecret, ttlSeconds, publicBaseUrl, clock);
    }

    public String sign(String rawUrl) {
        MediaReference media = parseInternalMedia(rawUrl);
        if (media == null) {
            return rawUrl;
        }
        long expires = Instant.now(clock).getEpochSecond() + ttlSeconds;
        String signature = calculateSignature(media.path(), expires);
        return publicBaseUrl + media.path()
                + "?expires=" + expires
                + "&signature=" + signature;
    }

    public String normalizeForStorage(String rawUrl) {
        MediaReference media = parseInternalMedia(rawUrl);
        return media == null ? rawUrl : media.path();
    }

    public String normalizeOwnedForStorage(String rawUrl, String expectedOwnerId) {
        MediaReference media = parseInternalMedia(rawUrl);
        if (media == null) {
            return rawUrl;
        }
        if (!Objects.equals(media.uid(), expectedOwnerId)) {
            throw new ForbiddenException("不能引用其他用户的媒体文件");
        }
        return media.path();
    }

    public boolean isValid(String mediaType, String uid, String fileName, long expires, String signature) {
        if (signature == null || signature.isBlank() || expires < Instant.now(clock).getEpochSecond()) {
            return false;
        }

        final String path;
        try {
            path = mediaPath(mediaType, uid, fileName);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        byte[] expected = calculateSignature(path, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    public QuestionEvalRule signRule(QuestionEvalRule rule) {
        return transformRule(rule, this::sign);
    }

    public QuestionEvalRule normalizeRuleForStorage(QuestionEvalRule rule) {
        return transformRule(rule, this::normalizeForStorage);
    }

    public QuestionEvalRule normalizeOwnedRuleForStorage(QuestionEvalRule rule, String expectedOwnerId) {
        return transformRule(rule, url -> normalizeOwnedForStorage(url, expectedOwnerId));
    }

    private QuestionEvalRule transformRule(QuestionEvalRule source, UnaryOperator<String> transform) {
        if (source == null) {
            return null;
        }

        QuestionEvalRule target = new QuestionEvalRule();
        target.setTypeName(source.getTypeName());
        target.setFullScore(source.getFullScore());
        target.setTimeLimit(source.getTimeLimit());
        target.setDefaultScore(source.getDefaultScore());
        target.setConditions(source.getConditions());
        target.setHintRules(transformHints(source.getHintRules(), transform));
        target.setEnableFuzzyEvaluation(source.getEnableFuzzyEvaluation());
        target.setKeywords(source.getKeywords());
        target.setKeyword(source.getKeyword());
        target.setEnforceOrder(source.getEnforceOrder());
        target.setFullScoreThreshold(source.getFullScoreThreshold());
        target.setAnswerText(source.getAnswerText());
        target.setWordType(source.getWordType());
        target.setChoices(transformChoices(source.getChoices(), transform));
        target.setCorrectChoices(source.getCorrectChoices());
        target.setSlots(transformSlots(source.getSlots(), transform));
        target.setActions(source.getActions());
        target.setInvalidActionPunishment(source.getInvalidActionPunishment());
        target.setDetailMode(source.getDetailMode());
        target.setCommandText(source.getCommandText());
        target.setImageUrl(transform.apply(source.getImageUrl()));
        target.setCoordinates(source.getCoordinates());
        return target;
    }

    private List<HintRule> transformHints(List<HintRule> hints, UnaryOperator<String> transform) {
        if (hints == null) {
            return null;
        }
        return hints.stream()
                .map(hint -> new HintRule(
                        hint.getHintText(),
                        transform.apply(hint.getHintAudioUrl()),
                        transform.apply(hint.getHintImageUrl()),
                        hint.getHintImageAssetPath(),
                        hint.getScoreLowBound(),
                        hint.getScoreHighBound(),
                        hint.getAdjustValue(),
                        hint.getScoreAdjustType()))
                .toList();
    }

    private List<Choice> transformChoices(List<Choice> choices, UnaryOperator<String> transform) {
        if (choices == null) {
            return null;
        }
        return choices.stream()
                .map(choice -> new Choice(
                        transform.apply(choice.getImageUrl()),
                        choice.getImageAssetPath(),
                        choice.getText()))
                .toList();
    }

    private List<ItemSlot> transformSlots(List<ItemSlot> slots, UnaryOperator<String> transform) {
        if (slots == null) {
            return null;
        }
        return slots.stream()
                .map(slot -> new ItemSlot(
                        slot.getItemName(),
                        transform.apply(slot.getItemImageUrl()),
                        slot.getItemImageAssetPath()))
                .toList();
    }

    private String calculateSignature(String path, long expires) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingSecret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((path + "\n" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成媒体签名", ex);
        }
    }

    private MediaReference parseInternalMedia(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.startsWith("assets/")) {
            return null;
        }

        final String path;
        try {
            URI uri = URI.create(rawUrl);
            path = uri.getPath();
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (path == null) {
            return null;
        }

        String[] segments = path.split("/");
        if (segments.length != 4) {
            return null;
        }
        String mediaType = segments[1];
        String uid = segments[2];
        String fileName = segments[3];
        if (!StaticResourcesConfig.IMAGE_DIR.equals(mediaType)
                && !StaticResourcesConfig.AUDIO_DIR.equals(mediaType)) {
            return null;
        }

        try {
            return new MediaReference(uid, mediaPath(mediaType, uid, fileName));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String mediaPath(String mediaType, String uid, String fileName) {
        if (StaticResourcesConfig.IMAGE_DIR.equals(mediaType)) {
            return StaticResourcesConfig.getImageUrlPath(uid, fileName);
        }
        if (StaticResourcesConfig.AUDIO_DIR.equals(mediaType)) {
            return StaticResourcesConfig.getAudioUrlPath(uid, fileName);
        }
        throw new IllegalArgumentException("非法媒体类型");
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("媒体公网基础 URL 不能为空");
        }
        String normalized = value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        final URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("媒体公网基础 URL 非法", ex);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("媒体公网基础 URL 必须是无查询参数的 HTTP(S) 地址");
        }
        return normalized;
    }

    private record MediaReference(String uid, String path) {
    }
}
