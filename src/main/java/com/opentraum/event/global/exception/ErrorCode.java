package com.opentraum.event.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 오류가 발생했습니다"),

    // Concert / Schedule
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "CO001", "공연을 찾을 수 없습니다"),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "CO002", "회차 정보를 찾을 수 없습니다"),

    // Seat
    INVALID_ZONE(HttpStatus.BAD_REQUEST, "S000", "유효하지 않은 구역입니다"),
    INVALID_GRADE_ZONE(HttpStatus.BAD_REQUEST, "S005", "선택한 등급에 해당 구역이 없습니다"),
    SEAT_ALREADY_TAKEN(HttpStatus.CONFLICT, "S001", "이미 선택된 좌석입니다"),
    SEAT_HOLD_EXPIRED(HttpStatus.GONE, "S002", "좌석 홀드가 만료되었습니다"),
    SEAT_HOLD_NOT_OWNED(HttpStatus.FORBIDDEN, "S006", "본인이 홀드한 좌석만 해제할 수 있습니다"),
    NO_AVAILABLE_SEATS(HttpStatus.NOT_FOUND, "S003", "잔여 좌석이 없습니다"),
    SOLD_OUT(HttpStatus.GONE, "S004", "매진되었습니다"),

    // Admin
    EVENT_EDIT_LOCKED(HttpStatus.CONFLICT, "A001", "예매 오픈 30분 전부터는 수정할 수 없습니다");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
