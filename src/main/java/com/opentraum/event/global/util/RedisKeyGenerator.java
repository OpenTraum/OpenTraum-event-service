package com.opentraum.event.global.util;

/**
 * Redis 키 생성 유틸리티
 */
public class RedisKeyGenerator {

    private RedisKeyGenerator() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }

    /**
     * 구역별 잔여 좌석 풀 키 (Set) - seats:{scheduleId}:{zone}
     */
    public static String seatsKey(Long scheduleId, String zone) {
        return String.format("seats:%d:%s", scheduleId, zone);
    }

    /**
     * 좌석 임시 홀드 키 (String+TTL) - hold:{scheduleId}:{zone}:{seatNo}
     */
    public static String holdKey(Long scheduleId, String zone, String seatNo) {
        return String.format("hold:%d:%s:%s", scheduleId, zone, seatNo);
    }

    /**
     * 등급별 잔여 재고 카운터 키 (String, atomic INCR/DECR 전용)
     * stock:{scheduleId}:{grade}
     */
    public static String stockKey(Long scheduleId, String grade) {
        return String.format("stock:%d:%s", scheduleId, grade);
    }
}
