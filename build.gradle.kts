plugins {
    java
    id("com.google.protobuf") version "0.9.5"
    id("me.champeau.jmh") version "0.7.3"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val protobufVersion = "4.31.1"
extra["protobuf-java.version"] = protobufVersion // Boot BOM의 protobuf 버전(4.35.1) 대신 protoc와 같은 버전 사용

dependencies {
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-java-util:$protobufVersion") // JsonFormat (Protobuf <-> JSON)
    implementation("org.springframework.boot:spring-boot-starter-webmvc") // rest: Spring MVC + 내장 Tomcat

    // JUnit, AssertJ, MockMvc. 버전은 Spring Boot BOM이 관리
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    jmh("com.fasterxml.jackson.core:jackson-databind:2.19.2") // 비교 대상 JSON 라이브러리
}

// src/main/proto/*.proto -> build/generated/sources/proto/main/java 에 Java 코드 생성
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
}

// src/jmh/java 의 벤치마크 실행: ./gradlew jmh (결과: build/results/jmh/results.txt)
jmh {
    fork = 1
    warmupIterations = 3
    warmup = "1s"
    iterations = 5
    timeOnIteration = "1s"
}
