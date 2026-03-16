package com.opentraum.event.domain.concert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeDetailResponse {

    private String id;           // grade 소문자 (예: "vip", "s", "a")
    private String label;        // grade 원본 (예: "VIP", "S", "A")
    private int price;
    private int totalSeats;      // zones에서 해당 grade의 seat_count 합산
    private int availableSeats;  // seats에서 해당 grade의 status='AVAILABLE' 카운트
}
