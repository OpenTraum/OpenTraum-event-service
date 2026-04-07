package com.opentraum.event.domain.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opentraum.event.config.OpenAiProperties;
import com.opentraum.event.domain.admin.dto.AdminDashboardResponse;
import com.opentraum.event.domain.admin.dto.AdminInsightResponse;
import com.opentraum.event.domain.concert.entity.Schedule;
import com.opentraum.event.domain.concert.repository.ScheduleRepository;
import com.opentraum.event.global.exception.BusinessException;
import com.opentraum.event.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AdminInsightService {

    private final AdminEventService adminEventService;
    private final ScheduleRepository scheduleRepository;
    private final WebClient webClient;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper objectMapper;

    public AdminInsightService(AdminEventService adminEventService,
                                ScheduleRepository scheduleRepository,
                                OpenAiProperties openAiProperties,
                                ObjectMapper objectMapper) {
        this.adminEventService = adminEventService;
        this.scheduleRepository = scheduleRepository;
        this.openAiProperties = openAiProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(openAiProperties.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    public Mono<AdminInsightResponse> generateInsights(String tenantId, Long scheduleId) {
        return Mono.zip(
                adminEventService.getDashboard(tenantId, scheduleId),
                scheduleRepository.findById(scheduleId)
        ).flatMap(tuple -> {
            AdminDashboardResponse dashboard = tuple.getT1();
            Schedule schedule = tuple.getT2();
            String metricsPrompt = buildMetricsPrompt(dashboard, schedule);

            return callOpenAi(metricsPrompt)
                    .map(response -> parseInsightResponse(scheduleId, response));
        }).onErrorMap(e -> !(e instanceof BusinessException), e -> {
            log.error("인사이트 생성 실패", e);
            return new BusinessException(ErrorCode.INTERNAL_ERROR);
        });
    }

    private String buildMetricsPrompt(AdminDashboardResponse dashboard, Schedule schedule) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 이벤트 현황\n");
        sb.append("- 공연명: ").append(dashboard.getTitle()).append("\n");
        sb.append("- 상태: ").append(dashboard.getStatus()).append("\n");
        sb.append("- 총 좌석: ").append(dashboard.getTotalSeats()).append("석\n");
        sb.append("- 판매 좌석: ").append(dashboard.getSoldSeats()).append("석\n");
        sb.append("- 잔여 좌석: ").append(dashboard.getAvailableSeats()).append("석\n");

        double totalRate = dashboard.getTotalSeats() > 0
                ? (double) dashboard.getSoldSeats() / dashboard.getTotalSeats() * 100 : 0;
        sb.append("- 전체 판매율: ").append(String.format("%.1f%%", totalRate)).append("\n");
        sb.append("- 배정 방식: ").append(schedule.getTrackPolicy()).append("\n");

        if (schedule.getTicketOpenAt() != null) {
            Duration remaining = Duration.between(LocalDateTime.now(), schedule.getTicketOpenAt());
            if (remaining.isNegative()) {
                Duration elapsed = Duration.between(schedule.getTicketOpenAt(), LocalDateTime.now());
                sb.append("- 티켓 오픈 후 경과: ").append(elapsed.toHours()).append("시간 ")
                        .append(elapsed.toMinutesPart()).append("분\n");
            } else {
                sb.append("- 티켓 오픈까지: ").append(remaining.toHours()).append("시간 ")
                        .append(remaining.toMinutesPart()).append("분 남음\n");
            }
        }

        if (schedule.getDateTime() != null) {
            Duration toEvent = Duration.between(LocalDateTime.now(), schedule.getDateTime());
            sb.append("- 공연까지: ").append(toEvent.toDays()).append("일 남음\n");
        }

        sb.append("\n## 등급별 현황\n");
        if (dashboard.getGradeStats() != null) {
            for (AdminDashboardResponse.GradeStat g : dashboard.getGradeStats()) {
                double rate = g.getTotalSeats() > 0
                        ? (double) g.getSoldSeats() / g.getTotalSeats() * 100 : 0;
                sb.append("- ").append(g.getGrade()).append(": ")
                        .append(g.getSoldSeats()).append("/").append(g.getTotalSeats())
                        .append(" (").append(String.format("%.1f%%", rate)).append(")")
                        .append(" | 잔여 ").append(g.getAvailableSeats()).append("석\n");
            }
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> callOpenAi(String metricsPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", openAiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", INSIGHT_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", metricsPrompt)
                ),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", INSIGHT_FUNCTION_SCHEMA
                )),
                "tool_choice", Map.of(
                        "type", "function",
                        "function", Map.of("name", "generate_insights")
                )
        );

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    @SuppressWarnings("unchecked")
    private AdminInsightResponse parseInsightResponse(Long scheduleId, Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            Map<String, Object> function = (Map<String, Object>) toolCalls.get(0).get("function");
            String arguments = (String) function.get("arguments");

            Map<String, Object> args = objectMapper.readValue(arguments, new TypeReference<>() {});

            String riskLevel = (String) args.get("riskLevel");
            List<String> insights = (List<String>) args.get("insights");
            List<Map<String, String>> actionsRaw = (List<Map<String, String>>) args.get("actions");

            List<AdminInsightResponse.RecommendedAction> actions = actionsRaw.stream()
                    .map(a -> AdminInsightResponse.RecommendedAction.builder()
                            .type(a.get("type"))
                            .title(a.get("title"))
                            .description(a.get("description"))
                            .urgency(a.get("urgency"))
                            .build())
                    .toList();

            return AdminInsightResponse.builder()
                    .scheduleId(scheduleId)
                    .riskLevel(riskLevel)
                    .insights(insights)
                    .actions(actions)
                    .build();
        } catch (Exception e) {
            log.error("인사이트 응답 파싱 실패: {}", response, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private static final String INSIGHT_SYSTEM_PROMPT = """
            당신은 티켓팅 플랫폼 OpenTraum의 운영 분석 AI 에이전트입니다.
            공연 주최자에게 실시간 판매 현황을 분석하여 운영 인사이트와 추천 액션을 제공합니다.

            ## 분석 기준
            1. 위험도(riskLevel) 판정:
               - CRITICAL: 특정 등급 95% 이상 매진 임박 또는 판매율 0%인 등급 존재
               - HIGH: 특정 등급 80% 이상 판매 또는 등급 간 판매율 편차 50%p 이상
               - MEDIUM: 전체 판매율 50% 이상이거나 특정 등급에 주의 필요
               - LOW: 안정적 판매 추세

            2. 인사이트(insights) 작성 규칙:
               - 구체적 숫자를 포함한 현황 분석 (예: "VIP 92% 판매, 약 8석 잔여")
               - 등급 간 판매 균형 분석
               - 매진 예상 시점 추정 (판매 속도 기반)
               - 최대 3~5개 핵심 인사이트

            3. 추천 액션(actions) 작성 규칙:
               - type: PRICE_ADJUST(가격 조정), TRACK_CHANGE(배정 방식 변경), CAPACITY_WARNING(용량 경고), MARKETING(마케팅 제안)
               - 구체적이고 실행 가능한 제안
               - urgency: HIGH/MEDIUM/LOW

            ## 주의사항
            - 한국어로 작성
            - 판매가 아직 시작되지 않은 경우(UPCOMING 상태)에도 유용한 사전 분석 제공
            - 과도한 경고는 지양, 실질적으로 도움이 되는 분석만 제공
            """;

    private static final Map<String, Object> INSIGHT_FUNCTION_SCHEMA = Map.of(
            "name", "generate_insights",
            "description", "판매 현황 분석 결과를 구조화하여 반환합니다",
            "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "riskLevel", Map.of(
                                    "type", "string",
                                    "enum", List.of("LOW", "MEDIUM", "HIGH", "CRITICAL"),
                                    "description", "전체 위험도 레벨"
                            ),
                            "insights", Map.of(
                                    "type", "array",
                                    "items", Map.of("type", "string"),
                                    "description", "핵심 인사이트 목록 (3~5개)"
                            ),
                            "actions", Map.of(
                                    "type", "array",
                                    "description", "추천 액션 목록",
                                    "items", Map.of(
                                            "type", "object",
                                            "properties", Map.of(
                                                    "type", Map.of("type", "string",
                                                            "enum", List.of("PRICE_ADJUST", "TRACK_CHANGE", "CAPACITY_WARNING", "MARKETING"),
                                                            "description", "액션 유형"),
                                                    "title", Map.of("type", "string", "description", "액션 제목"),
                                                    "description", Map.of("type", "string", "description", "상세 설명"),
                                                    "urgency", Map.of("type", "string",
                                                            "enum", List.of("LOW", "MEDIUM", "HIGH"),
                                                            "description", "긴급도")
                                            ),
                                            "required", List.of("type", "title", "description", "urgency")
                                    )
                            )
                    ),
                    "required", List.of("riskLevel", "insights", "actions")
            )
    );
}
