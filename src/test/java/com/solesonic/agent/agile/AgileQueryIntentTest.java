package com.solesonic.agent.agile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgileQueryIntentTest {

    @Test
    void hasExplicitScope_withIssueKey_returnsTrue() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123"), null, null, null, "", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isTrue();
    }

    @Test
    void hasExplicitScope_withAssignee_returnsTrue() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), "someone@example.com", null, null, "", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isTrue();
    }

    @Test
    void hasExplicitScope_withReporter_returnsTrue() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, "someone@example.com", null, "", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isTrue();
    }

    @Test
    void hasExplicitScope_withOlderThanDays_returnsTrue() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, 30, "", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isTrue();
    }

    @Test
    void hasExplicitScope_withSupplementalJql_returnsTrue() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "status = Done", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isTrue();
    }

    @Test
    void hasExplicitScope_withEmptyEverything_returnsFalse() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isFalse();
    }

    @Test
    void hasExplicitScope_withNullEverything_returnsFalse() {
        AgileQueryIntent intent = new AgileQueryIntent(
                null, null, null, null, null, "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isFalse();
    }

    @Test
    void hasExplicitScope_withBlankAssignee_returnsFalse() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), "   ", null, null, "", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isFalse();
    }

    @Test
    void hasExplicitScope_withBlankSupplementalJql_returnsFalse() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "  ", "TRANSITION", 0, "Done");

        assertThat(intent.hasExplicitScope()).isFalse();
    }

    @Test
    void isTransitionQuery_withTransitionIntent_returnsTrue() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123"), null, null, null, "", "TRANSITION", 0, "Done");

        assertThat(intent.isTransitionQuery()).isTrue();
    }

    @Test
    void isTransitionQuery_withCountIntent_returnsFalse() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "", "COUNT", 0, null);

        assertThat(intent.isTransitionQuery()).isFalse();
    }

    @Test
    void resolvedStartAt_withNullStartAt_returnsZero() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "", "LIST", null, null);

        assertThat(intent.resolvedStartAt()).isZero();
    }

    @Test
    void resolvedStartAt_withExplicitStartAt_returnsValue() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "", "LIST", 15, null);

        assertThat(intent.resolvedStartAt()).isEqualTo(15);
    }
}
