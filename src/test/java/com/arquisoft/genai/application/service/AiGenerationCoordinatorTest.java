package com.arquisoft.genai.application.service;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiGenerationCoordinator Tests")
class AiGenerationCoordinatorTest {

    private AiGenerationCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new AiGenerationCoordinator();
        ReflectionTestUtils.setField(coordinator, "requestCacheTtlSeconds", 300L);
        ReflectionTestUtils.setField(coordinator, "maxConcurrentRequests", 1);
        ReflectionTestUtils.setField(coordinator, "failureThreshold", 3);
        ReflectionTestUtils.setField(coordinator, "circuitOpenSeconds", 60L);
        ReflectionTestUtils.invokeMethod(coordinator, "init");
    }

    @Test
    @DisplayName("execute: deduplicates concurrent requests for the same input")
    void execute_deduplicatesConcurrentRequests() throws Exception {
        ArchitectureInput input = ArchitectureInput.builder()
                .domain("fintech")
                .qualityAttributes(List.of("security", "scalability"))
                .techStackConstraints(List.of("Java", "PostgreSQL"))
                .naturalLanguageDescription("Payment platform")
                .build();

        ArchitectureOutput output = ArchitectureOutput.builder()
                .style("Microservices")
                .qualityAttributes(List.of("security"))
                .diagrams(Map.of("C4-Context", "@startuml\n@enduml"))
                .documentation("doc")
                .techStack(List.of("Java 17"))
                .decisions(List.of("Use JWT"))
                .build();

        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch supplierEntered = new CountDownLatch(1);
        CountDownLatch releaseSupplier = new CountDownLatch(1);

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> coordinator.execute(input, () -> {
                invocations.incrementAndGet();
                supplierEntered.countDown();
                awaitLatch(releaseSupplier);
                return output;
            }));

            assertThat(supplierEntered.await(5, TimeUnit.SECONDS)).isTrue();

            var second = executor.submit(() -> coordinator.execute(input, () -> {
                invocations.incrementAndGet();
                return output;
            }));

            releaseSupplier.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(output);
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(output);
            assertThat(invocations.get()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}