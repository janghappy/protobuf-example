package com.example.protobuf.benchmark;

import com.example.protobuf.order.Order;
import com.example.protobuf.order.OrderItem;
import com.example.protobuf.order.OrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 같은 주문을 Jackson(JSON), Protobuf 바이너리, JsonFormat(Protobuf -> JSON)으로
 * 직렬화(객체 -> byte[]) / 역직렬화(byte[] -> 객체)하는 처리량 비교. 설명은 docs/benchmark/benchmark.md
 * <p>
 * 네트워크로 주고받는 상황을 가정해 세 방식 모두 byte[]를 입출력으로 쓴다.
 * <p>
 * 측정 흐름: @Benchmark 메서드 6개 x @Param 3개 = 18개 조합을 하나씩 잰다.
 * 조합마다 새 JVM -> itemCount 주입 -> setUp() 1회 -> 워밍업 -> 측정(1초 동안 메서드를 반복 호출한 횟수 = ops/s).
 * setUp()과 @Benchmark 메서드는 JMH가 빌드 때 생성한 클래스(build/jmh-generated-sources)가 호출한다.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SerializationBenchmark {

    @Param({"1", "10", "1000"})
    int itemCount;

    // ObjectMapper, Printer, Parser는 스레드 안전하고 생성 비용이 크므로 재사용한다
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonFormat.Printer jsonPrinter = JsonFormat.printer().omittingInsignificantWhitespace();
    private final JsonFormat.Parser jsonParser = JsonFormat.parser();

    private OrderDto dto;
    private Order proto;
    private byte[] jacksonBytes;
    private byte[] protobufBytes;
    private byte[] jsonFormatBytes;

    // 조합마다 1회 호출 (Level.Trial). 측정 시간에 포함되지 않는다
    @Setup
    public void setUp() throws Exception {
        List<OrderDto.OrderItemDto> dtoItems = new ArrayList<>();
        Order.Builder builder = Order.newBuilder().setId(1L).setCustomerName("kim").setStatus(OrderStatus.ORDER_STATUS_PAID);
        for (int i = 1; i <= itemCount; i++) {
            dtoItems.add(new OrderDto.OrderItemDto("product-" + i, i, i * 1_000L));
            builder.addItems(OrderItem.newBuilder().setProductName("product-" + i).setQuantity(i).setPrice(i * 1_000L));
        }
        dto = new OrderDto(1L, "kim", "ORDER_STATUS_PAID", dtoItems);
        proto = builder.build();

        // 역직렬화 벤치마크의 입력. 호출마다 만들면 직렬화 시간까지 섞이므로 미리 한 번만 만든다
        jacksonBytes = objectMapper.writeValueAsBytes(dto);
        protobufBytes = proto.toByteArray();
        jsonFormatBytes = jsonPrinter.print(proto).getBytes(StandardCharsets.UTF_8);
    }

    // 결과를 반환해야 JMH가 Blackhole로 소비해 JIT가 계산을 지우지 못한다 (void 금지)

    // ---------- 직렬화: 객체 -> byte[] ----------

    @Benchmark
    public byte[] serializeJackson() throws Exception {
        return objectMapper.writeValueAsBytes(dto);
    }

    @Benchmark
    public byte[] serializeProtobuf() {
        return proto.toByteArray();
    }

    @Benchmark
    public byte[] serializeJsonFormat() throws Exception {
        return jsonPrinter.print(proto).getBytes(StandardCharsets.UTF_8);
    }

    // ---------- 역직렬화: byte[] -> 객체 ----------

    @Benchmark
    public OrderDto deserializeJackson() throws Exception {
        return objectMapper.readValue(jacksonBytes, OrderDto.class);
    }

    @Benchmark
    public Order deserializeProtobuf() throws Exception {
        return Order.parseFrom(protobufBytes);
    }

    @Benchmark
    public Order deserializeJsonFormat() throws Exception {
        Order.Builder builder = Order.newBuilder();
        jsonParser.merge(new String(jsonFormatBytes, StandardCharsets.UTF_8), builder);
        return builder.build();
    }
}
