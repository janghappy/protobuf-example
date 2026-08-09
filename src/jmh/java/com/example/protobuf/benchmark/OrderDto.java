package com.example.protobuf.benchmark;

import java.util.List;

/**
 * Jackson 비교용 POJO. order.proto의 Order와 같은 필드를 가진다.
 * JSON 필드 이름도 JsonFormat 출력과 같다 (customerName, productName ...).
 */
public record OrderDto(long id, String customerName, String status, List<OrderItemDto> items) {

    public record OrderItemDto(String productName, int quantity, long price) {
    }
}
