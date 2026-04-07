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
                    .category((String) args.getOrDefault("category", "OTHER"))
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
            오늘 날짜: """ + java.time.LocalDate.now() + """


            ## 핵심 규칙 (반드시 준수)
            1. **사용자가 좌석 수를 명시하면 반드시 그 수를 totalSeats로 사용하라.**
               프리셋 공연장의 기본 좌석과 다르더라도 사용자 지정 값이 우선이다.
               예: "올림픽홀 500석" → totalSeats=500 (올림픽홀 프리셋 2400석 무시)
            2. 구역별 좌석 수 합계 = totalSeats (정확히 일치해야 함)
            3. 등급별 좌석 수 합계 = 해당 등급의 전체 구역 좌석 수 합

            ## trackPolicy 기준
            - 100석 이하: LIVE_ONLY
            - 100~1000석: LOTTERY_ONLY
            - 1000석 이상: DUAL_TRACK

            ## 등급(grade) 구성
            - 1000석 이상: VIP / R / S / A (4등급, 비율 약 10/20/35/35)
            - 300~999석: VIP / S / A (3등급, 비율 약 15/40/45)
            - 100~299석: S / A (2등급, 비율 약 40/60)
            - 100석 미만: 전석 (단일 등급)
            - 가격: VIP 15~18만, R 11~13만, S 8~10만, A 5~7만 (한국 원화)

            ## 구역(zone) 구성
            - 공연장 프리셋이 있으면 구역명 참고하되, 좌석 수는 사용자 지정 totalSeats에 맞게 비례 배분
            - 프리셋이 없으면 1층/2층 또는 A구역/B구역 등 적절히 생성

            ## 날짜
            - 명시되지 않으면 현재 날짜 기준 1~2개월 후 저녁 7시(19:00)로 설정
            - 반드시 미래 날짜여야 함 (ISO-8601 형식)

            ## 카테고리 자동 분류
            - 제목, 아티스트, 공연장 정보를 기반으로 적절한 카테고리를 선택
            - CONCERT: 음악 공연, 콘서트, 라이브
            - SPORTS: 축구, 야구, 농구, 스포츠 경기
            - MUSICAL: 뮤지컬, 연극, 오페라
            - FANMEETING: 팬미팅, 팬사인회
            - FESTIVAL: 페스티벌, 축제
            - EXHIBITION: 전시회, 박람회
            - OTHER: 분류 불가

            ## 아티스트
            - 명시되지 않으면 null

            ## 공연장 프리셋 (구역 참고용, 좌석 수는 사용자 지정 우선)
            - 올림픽홀: 기본 2,400석 (1층, 2층)
            - 블루스퀘어: 기본 1,766석 (1층, 2층)
            - 예스24 라이브홀: 기본 2,000석 (스탠딩)
            - 소규모 공연장: 기본 300석 (전석)
            - 세종문화회관 대극장: 기본 3,022석 (1층, 2층, 3층)
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
                            Map.entry("category", Map.of(
                                    "type", "string",
                                    "enum", List.of("CONCERT", "SPORTS", "MUSICAL", "FANMEETING", "FESTIVAL", "EXHIBITION", "OTHER"),
                                    "description", "이벤트 카테고리"
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
                    "required", List.of("title", "venue", "totalSeats", "trackPolicy", "category", "grades", "zones")
            )
    );
}
