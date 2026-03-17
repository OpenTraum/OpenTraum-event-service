package com.opentraum.event.domain.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentraum.event.config.OpenAiProperties;
import com.opentraum.event.domain.admin.dto.AiGenerateResponse;
import com.opentraum.event.global.exception.BusinessException;
import com.opentraum.event.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiEventGenerateService {

    private final WebClient webClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public AiEventGenerateService(OpenAiProperties openAiProperties, ObjectMapper objectMapper) {
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(openAiProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    public Mono<AiGenerateResponse> generate(String prompt) {
        Map<String, Object> requestBody = buildRequestBody(prompt);

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::parseResponse)
                .doOnSuccess(r -> log.info("AI 이벤트 생성 완료: title={}, venue={}, seats={}",
                        r.getTitle(), r.getVenue(), r.getTotalSeats()))
                .onErrorMap(e -> !(e instanceof BusinessException),
                        e -> {
                            log.error("OpenAI API 호출 실패", e);
                            return new BusinessException(ErrorCode.INTERNAL_ERROR);
                        });
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "model", openAiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt)
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", FUNCTION_SCHEMA
                )),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", "generate_event_config")
                )
        );
    }

    @SuppressWarnings("unchecked")
    private AiGenerateResponse parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
            String arguments = (String) function.get("arguments");

            Map<String, Object> args = objectMapper.readValue(arguments, new TypeReference<>() {});

            List<AiGenerateResponse.GradeConfig> grades = ((List<Map<String, Object>>) args.get("grades")).stream()
                    .map(g -> AiGenerateResponse.GradeConfig.builder()
                            .grade((String) g.get("grade"))
                            .price(((Number) g.get("price")).intValue())
                            .seatCount(((Number) g.get("seatCount")).intValue())
                            .build())
                    .toList();

            List<AiGenerateResponse.ZoneConfig> zones = ((List<Map<String, Object>>) args.get("zones")).stream()
                    .map(z -> AiGenerateResponse.ZoneConfig.builder()
                            .zone((String) z.get("zone"))
                            .grade((String) z.get("grade"))
                            .seatCount(((Number) z.get("seatCount")).intValue())
                            .build())
                    .toList();

            return AiGenerateResponse.builder()
                    .title((String) args.get("title"))
                    .artist((String) args.get("artist"))
                    .venue((String) args.get("venue"))
                    .dateTime((String) args.get("dateTime"))
                    .totalSeats(((Number) args.get("totalSeats")).intValue())
                    .trackPolicy((String) args.get("trackPolicy"))
                    .grades(grades)
                    .zones(zones)
                    .build();
        } catch (Exception e) {
            log.error("OpenAI 응답 파싱 실패: {}", response, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static final String SYSTEM_PROMPT = """
            당신은 티켓팅 플랫폼 OpenTraum의 이벤트 구성 전문가입니다.
            사용자의 자연어 입력을 분석하여 공연/이벤트 구성을 생성합니다.

            ## 규칙
            1. trackPolicy 추천 기준:
               - 100석 이하: LIVE_ONLY (소규모, 대기열 불필요)
               - 100~1000석: LIVE_ONLY 또는 LOTTERY_ONLY (상황에 따라 판단)
               - 1000석 이상: DUAL_TRACK (트래픽 분산 필요)

            2. 등급(grade) 구성:
               - 대규모: VIP / R / S / A 등 다양한 등급
               - 소규모: 전석 단일 등급도 가능
               - 가격은 한국 원화 기준으로 현실적으로 설정

            3. 구역(zone) 구성:
               - 각 구역은 하나의 등급에 매핑
               - 구역별 좌석 수 합 = totalSeats
               - 등급별 좌석 수 합 = 해당 등급의 전체 구역 좌석 수 합

            4. 날짜가 명시되지 않으면 적절한 미래 날짜를 설정 (ISO-8601 형식)
            5. 아티스트가 명시되지 않으면 null로 설정

            ## 공연장 프리셋 참고
            - 올림픽홀: 2,400석 (1층 1200, 2층 1200)
            - 블루스퀘어: 1,766석 (1층 900, 2층 866)
            - 예스24 라이브홀: 2,000석 (스탠딩 2000)
            - 소규모 공연장: 300석 (전석 300)
            - 세종문화회관 대극장: 3,022석 (1층 1500, 2층 800, 3층 722)
            """;

    private static final Map<String, Object> FUNCTION_SCHEMA = Map.of(
            "name", "generate_event_config",
            "description", "공연/이벤트의 전체 구성을 생성합니다",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.ofEntries(
                            Map.entry("title", Map.of("type", "string", "description", "공연 제목")),
                            Map.entry("artist", Map.of("type", "string", "description", "아티스트/출연자 이름 (없으면 null)")),
                            Map.entry("venue", Map.of("type", "string", "description", "공연장 이름")),
                            Map.entry("dateTime", Map.of("type", "string", "description", "공연 날짜/시간 (ISO-8601)")),
                            Map.entry("totalSeats", Map.of("type", "integer", "description", "총 좌석 수")),
                            Map.entry("trackPolicy", Map.of(
                                    "type", "string",
                                    "enum", List.of("LOTTERY_ONLY", "LIVE_ONLY", "DUAL_TRACK"),
                                    "description", "배정 방식 (100석 이하: LIVE_ONLY, 100~1000: LIVE_ONLY/LOTTERY_ONLY, 1000+: DUAL_TRACK)"
                            )),
                            Map.entry("grades", Map.of(
                                    "type", "array",
                                    "description", "등급 구성 목록",
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "grade", Map.of("type", "string", "description", "등급명 (예: VIP, R, S, A)"),
                                                    "price", Map.of("type", "integer", "description", "가격 (원)"),
                                                    "seatCount", Map.of("type", "integer", "description", "해당 등급 좌석 수")
                                            ),
                                            "required", List.of("grade", "price", "seatCount")
                                    )
                            )),
                            Map.entry("zones", Map.of(
                                    "type", "array",
                                    "description", "구역 구성 목록",
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "zone", Map.of("type", "string", "description", "구역명 (예: 1층, 2층, 스탠딩)"),
                                                    "grade", Map.of("type", "string", "description", "해당 구역의 등급"),
                                                    "seatCount", Map.of("type", "integer", "description", "구역 좌석 수")
                                            ),
                                            "required", List.of("zone", "grade", "seatCount")
                                    )
                            ))
                    ),
                    "required", List.of("title", "venue", "totalSeats", "trackPolicy", "grades", "zones")
            )
    );
}
