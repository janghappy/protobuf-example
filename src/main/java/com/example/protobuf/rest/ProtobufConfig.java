package com.example.protobuf.rest;

import com.google.protobuf.util.JsonFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.http.converter.protobuf.ProtobufJsonFormatHttpMessageConverter;

@Configuration
public class ProtobufConfig {

    // 등록하지 않으면 Protobuf 메시지를 Jackson이 처리하려다 실패한다 (Type definition error)
    // 기본 Jackson 컨버터를 대체하지 않고 맨 앞에 추가된다. Protobuf 메시지(Message 하위 클래스)만 맡고 DTO 등은 그대로 Jackson이 처리한다
    // application/x-protobuf는 toByteArray()/parseFrom(), application/json은 JsonFormat으로 변환한다
    @Bean
    ProtobufHttpMessageConverter protobufHttpMessageConverter() {
        // 기본 printer는 들여쓰기를 넣으므로 공백 없는 printer를 넘긴다
        return new ProtobufJsonFormatHttpMessageConverter(
                JsonFormat.parser(), JsonFormat.printer().omittingInsignificantWhitespace());
    }
}
