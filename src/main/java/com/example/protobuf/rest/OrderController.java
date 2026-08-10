package com.example.protobuf.rest;

import com.example.protobuf.order.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 반환 타입과 파라미터가 Protobuf 메시지(Order)다.
 * JSON / 바이너리 변환은 컨트롤러가 아니라 ProtobufHttpMessageConverter가 Accept, Content-Type 헤더를 보고 한다.
 * <p>
 * API를 두 개 둔 이유: 변환의 두 방향을 각각 확인하기 위해서다.
 * <ul>
 *   <li>GET: 응답 쓰기 (Order -> 바이너리/JSON). Accept로 형식 선택, 지원하지 않으면 406</li>
 *   <li>POST: 요청 읽기 (바이너리/JSON -> Order). Content-Type으로 형식 지정, 읽지 못하면 415.
 *       GET에는 요청 본문이 없어서 이쪽은 POST로만 확인할 수 있다</li>
 * </ul>
 * 저장소가 비어 있으므로 테스트는 POST로 주문을 만든 뒤 GET으로 조회한다.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    // 예제용 메모리 저장소
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @GetMapping("/{id}")
    public Order get(@PathVariable long id) {
        Order order = orders.get(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return order;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(@RequestBody Order request) {
        Order order = request.toBuilder().setId(sequence.incrementAndGet()).build();
        orders.put(order.getId(), order);
        return order;
    }
}
