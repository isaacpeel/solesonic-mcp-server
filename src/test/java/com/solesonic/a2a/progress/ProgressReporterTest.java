package com.solesonic.a2a.progress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressReporterTest {

    private List<Integer> capturedPercents;
    private List<String> capturedMessages;
    private ProgressReporter reporter;

    @BeforeEach
    void setUp() {
        capturedPercents = new ArrayList<>();
        capturedMessages = new ArrayList<>();
        reporter = new ProgressReporter((percent, message) -> {
            capturedPercents.add(percent);
            capturedMessages.add(message);
        });
    }

    @Test
    void emit_normalPercent_delegatesToEmitter() {
        reporter.emit(50, "halfway");

        assertThat(capturedPercents).containsExactly(50);
        assertThat(capturedMessages).containsExactly("halfway");
    }

    @Test
    void emit_percentAbove100_clampedTo100() {
        reporter.emit(150, "done");

        assertThat(capturedPercents).containsExactly(100);
    }

    @Test
    void emit_percentBelow0_clampedTo0() {
        reporter.emit(-5, "start");

        assertThat(capturedPercents).containsExactly(0);
    }

    @Test
    void emit_percentExactly0_accepted() {
        reporter.emit(0, "start");

        assertThat(capturedPercents).containsExactly(0);
    }

    @Test
    void emit_percentExactly100_accepted() {
        reporter.emit(100, "done");

        assertThat(capturedPercents).containsExactly(100);
    }

    @Test
    void emit_nullMessage_replacedWithEmpty() {
        reporter.emit(10, null);

        assertThat(capturedMessages).containsExactly("");
    }

    @Test
    void emit_monotonic_neverGoesBack() {
        reporter.emit(60, "step one");
        reporter.emit(40, "step two");

        assertThat(capturedPercents).containsExactly(60, 60);
    }

    @Test
    void emit_monotonic_advancesForward() {
        reporter.emit(30, "step one");
        reporter.emit(70, "step two");

        assertThat(capturedPercents).containsExactly(30, 70);
    }

    @Test
    void emit_monotonic_equalPercent_emitsSame() {
        reporter.emit(50, "first");
        reporter.emit(50, "second");

        assertThat(capturedPercents).containsExactly(50, 50);
    }
}
