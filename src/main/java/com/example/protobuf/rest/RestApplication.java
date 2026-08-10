package com.example.protobuf.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 같은 HTTP API에서 JSON과 Protobuf 바이너리를 둘 다 지원하는 예제. 실행: ./gradlew bootRun
 */
@SpringBootApplication
public class RestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestApplication.class, args);
    }
}
