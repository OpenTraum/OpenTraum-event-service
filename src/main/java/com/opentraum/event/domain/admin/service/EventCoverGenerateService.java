package com.opentraum.event.domain.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentraum.event.config.FluxProperties;
import com.opentraum.event.config.OpenAiProperties;
import com.opentraum.event.config.S3Properties;
import com.opentraum.event.domain.admin.dto.CoverGenerateRequest;
import com.opentraum.event.domain.admin.dto.CoverGenerateResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventCoverGenerateService {

    private final OpenAiProperties openAiProperties;
    private final FluxProperties fluxProperties;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    private WebClient nemotronClient;
    private WebClient fluxClient;

    @PostConstruct
    void init() {
        nemotronClient = WebClient.builder()
                .baseUrl(openAiProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        fluxClient = WebClient.builder()
                .baseUrl(fluxProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
                .build();
    }

    public Mono<CoverGenerateResponse> generate(CoverGenerateRequest req) {
        long t0 = System.currentTimeMillis();
        return buildPromptDetails(req)
                .flatMap(details -> callFluxAndUpload(req, details, t0))
                .onErrorResume(e -> {
                    log.error("cover generate failed for category={}: {}", req.getCategory(), e.toString());
                    return Mono.just(fallbackResponse(req.getCategory(), t0));
                });
    }

    /** Nemotron build_image_prompt function call → mood/season/palette/accent_objects JSON. */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> buildPromptDetails(CoverGenerateRequest req) {
        Map<String, Object> body = Map.of(
                "model", openAiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", IMAGE_PROMPT_SYSTEM),
                        Map.of("role", "user", "content", composeUserMeta(req))
                ),
                "tools", List.of(Map.of("type", "function", "function", BUILD_IMAGE_PROMPT_SCHEMA)),
                "tool_choice", Map.of("type", "function",
                        "function", Map.of("name", "build_image_prompt"))
        );
        return nemotronClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(resp -> {
                    try {
                        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
                        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                        List<Map<String, Object>> tools = (List<Map<String, Object>>) message.get("tool_calls");
                        Map<String, Object> fn = (Map<String, Object>) tools.get(0).get("function");
                        String args = (String) fn.get("arguments");
                        return objectMapper.readValue(args, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        log.warn("parse build_image_prompt failed, using defaults: {}", e.toString());
                        return defaultDetails();
                    }
                })
                .onErrorReturn(defaultDetails());
    }

    private Map<String, Object> defaultDetails() {
        return Map.of(
                "mood", "cinematic",
                "season", "any",
                "time_of_day", "golden_hour",
                "palette", List.of("deep blue", "violet"),
                "accent_objects", List.of()
        );
    }

    private String composeUserMeta(CoverGenerateRequest r) {
        return "category=" + r.getCategory()
                + ", title=" + r.getTitle()
                + ", venue=" + r.getVenue()
                + (r.getArtist() != null ? ", artist=" + r.getArtist() : "")
                + (r.getDateTime() != null ? ", dateTime=" + r.getDateTime() : "");
    }

    @SuppressWarnings("unchecked")
    private Mono<CoverGenerateResponse> callFluxAndUpload(
            CoverGenerateRequest req, Map<String, Object> details, long t0) {
        String prompt = composePositive(req.getCategory(), details);
        String negative = composeNegative(req.getCategory());
        String correlationId = UUID.randomUUID().toString();
        int candidates = fluxProperties.getCandidates();

        Map<String, Object> fluxBody = new LinkedHashMap<>();
        fluxBody.put("prompt", prompt);
        fluxBody.put("negative_prompt", negative);
        fluxBody.put("num_images", candidates);
        fluxBody.put("width", fluxProperties.getWidth());
        fluxBody.put("height", fluxProperties.getHeight());
        fluxBody.put("num_inference_steps", fluxProperties.getSteps());
        fluxBody.put("guidance_scale", fluxProperties.getGuidance());
        fluxBody.put("seed", Math.abs(req.getCategory().hashCode()));

        return fluxClient.post()
                .uri("/generate")
                .bodyValue(fluxBody)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(fluxProperties.getRequestTimeoutSeconds()))
                .flatMap(resp -> Mono.fromCallable(() -> {
                    List<String> imagesB64 = (List<String>) resp.get("images");
                    String model = (String) resp.getOrDefault("model", "unknown");
                    List<String> urls = new ArrayList<>();
                    for (int i = 0; i < imagesB64.size(); i++) {
                        byte[] png = Base64.getDecoder().decode(imagesB64.get(i));
                        String key = "events/" + correlationId + "/cover/" + i + ".png";
                        s3Client.putObject(
                                PutObjectRequest.builder()
                                        .bucket(s3Properties.getBucket())
                                        .key(key)
                                        .contentType("image/png")
                                        .build(),
                                RequestBody.fromBytes(png));
                        urls.add(presign(key));
                    }
                    double seconds = (System.currentTimeMillis() - t0) / 1000.0;
                    log.info("cover generate ok category={} candidates={} elapsed={}s",
                            req.getCategory(), urls.size(), seconds);
                    return CoverGenerateResponse.builder()
                            .urls(urls)
                            .fallback(false)
                            .model(model)
                            .seconds(seconds)
                            .build();
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    private String presign(String key) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(s3Properties.getBucket()).key(key).build();
        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(s3Properties.getPresignHours()))
                .getObjectRequest(get).build();
        return s3Presigner.presignGetObject(req).url().toString();
    }

    private CoverGenerateResponse fallbackResponse(String category, long t0) {
        String cat = SUPPORTED.contains(category) ? category : "OTHER";
        String url = presign("defaults/" + cat + ".webp");
        double seconds = (System.currentTimeMillis() - t0) / 1000.0;
        return CoverGenerateResponse.builder()
                .urls(List.of(url))
                .fallback(true)
                .model("default-banner")
                .seconds(seconds)
                .build();
    }

    private static final Set<String> SUPPORTED = Set.of(
            "CONCERT", "SPORTS", "MUSICAL", "FANMEETING", "FESTIVAL", "EXHIBITION", "OTHER");

    @SuppressWarnings("unchecked")
    private String composePositive(String category, Map<String, Object> d) {
        String mood = (String) d.getOrDefault("mood", "cinematic");
        String tod = (String) d.getOrDefault("time_of_day", "golden_hour");
        List<String> palette = (List<String>) d.getOrDefault("palette", List.of("deep blue", "violet"));
        List<String> accents = (List<String>) d.getOrDefault("accent_objects", List.of());
        String pal = String.join(" and ", palette);
        String acc = accents.isEmpty() ? "" : ", " + String.join(", ", accents);

        String prefix = "professional event poster background, photorealistic, high detail, "
                + "no people, empty venue, atmospheric, cinematic composition, 16:9 aspect ratio, ";
        return switch (category) {
            case "CONCERT" -> prefix + "empty concert stage, dramatic stage lighting, " + pal
                    + " stage gel lights, atmospheric haze, spotlights cutting through fog, "
                    + "mounted speakers and rigging visible, " + mood + " mood, " + tod + " ambience"
                    + acc + ", polished black stage floor reflecting lights";
            case "SPORTS" -> prefix + "empty modern stadium interior, vibrant green turf, "
                    + "illuminated scoreboard glowing, stadium lights from above, tiered empty seats in distance, "
                    + pal + " team color accents on banners, " + tod + " sky visible through open roof, "
                    + mood + " atmosphere" + acc + ", panoramic wide shot";
            case "MUSICAL" -> prefix + "classical theater stage, deep red velvet curtains parted slightly, "
                    + "ornate gilded proscenium arch, grand crystal chandelier, warm amber stage lighting, "
                    + "polished wooden stage floor, empty orchestra pit visible in foreground, "
                    + pal + " accent lighting, " + mood + " " + tod + " atmosphere" + acc + ", baroque architectural details";
            case "FANMEETING" -> prefix + "cozy intimate event space, warm fairy lights strung overhead, "
                    + "pastel " + pal + " balloon arch, decorative bunting and banners, "
                    + "small empty stage with single chair and microphone stand, soft glowing string lights, "
                    + mood + " cheerful atmosphere" + acc + ", flower arrangements on tables";
            case "FESTIVAL" -> prefix + "open-air festival grounds at " + tod + ", "
                    + "glowing paper lanterns strung between poles, vibrant " + pal + " banners flapping, "
                    + "empty wooden stage with light truss in background, food stall tents in distance closed, "
                    + "string lights crisscrossing sky, " + mood + " celebratory mood" + acc;
            case "EXHIBITION" -> prefix + "minimalist gallery interior, polished concrete floor, "
                    + "soft track lighting from above, white walls with empty picture frames, "
                    + "display pedestals with abstract sculptures, " + pal + " accent wall, "
                    + "floor-to-ceiling windows with " + tod + " natural light, "
                    + mood + " contemplative atmosphere" + acc + ", architectural depth";
            default -> prefix + "abstract modern event banner background, "
                    + "geometric shapes and gradients, " + pal + " color flow, "
                    + "soft bokeh lights, clean minimalist composition, " + mood + " atmosphere"
                    + acc + ", sophisticated and versatile";
        };
    }

    private String composeNegative(String category) {
        String common = "people, person, faces, portrait, real person, celebrity, identifiable individual, "
                + "crowd, audience silhouette, hands, fingers, body parts, "
                + "text, korean text, english text, watermark, logo, signature, "
                + "low quality, blurry, distorted, deformed, disfigured, bad anatomy, "
                + "artifacts, jpeg artifacts, oversaturated, cartoon, anime, 3d render";
        String extra = switch (category) {
            case "CONCERT" -> "musicians, band members, performers, ";
            case "SPORTS" -> "athletes, players, referees, ";
            case "MUSICAL" -> "actors, dancers, performers in costume, ";
            case "FANMEETING" -> "idol, k-pop star, fans, autograph, ";
            case "FESTIVAL" -> "dancers, performers, ";
            case "EXHIBITION" -> "visitors, gallery viewers, ";
            default -> "";
        };
        return extra + common;
    }

    private static final String IMAGE_PROMPT_SYSTEM = """
            당신은 OpenTraum의 이벤트 이미지 prompt 디자이너입니다.
            주어진 이벤트 메타데이터(카테고리/제목/공연장/아티스트/날짜)를 분석해
            적절한 분위기·시즌·시간대·색상 팔레트·소품을 추론하세요.

            반드시 build_image_prompt function을 호출하세요. 다음 규칙을 지킵니다.
            - 인물명/실제 IP는 절대 포함시키지 않습니다 (별도 negative prompt로도 차단됨).
            - palette는 2~4개의 시각적으로 어울리는 색상 영문 키워드.
            - accent_objects는 카테고리에 어울리는 소품 영문 키워드 0~5개 (인물 X).
            """;

    private static final Map<String, Object> BUILD_IMAGE_PROMPT_SCHEMA = Map.of(
            "name", "build_image_prompt",
            "description", "이벤트 메타에서 이미지 생성용 시각 디테일을 추론합니다",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "mood", Map.of("type", "string",
                                    "enum", List.of("energetic", "intimate", "grand", "festive", "calm", "cinematic")),
                            "season", Map.of("type", "string",
                                    "enum", List.of("spring", "summer", "autumn", "winter", "any")),
                            "time_of_day", Map.of("type", "string",
                                    "enum", List.of("day", "golden_hour", "dusk", "night")),
                            "palette", Map.of("type", "array",
                                    "items", Map.of("type", "string"),
                                    "minItems", 2, "maxItems", 4),
                            "accent_objects", Map.of("type", "array",
                                    "items", Map.of("type", "string"),
                                    "maxItems", 5)
                    ),
                    "required", List.of("mood", "season", "time_of_day", "palette")
            )
    );
}
