package com.solesonic.agent.agile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgileJqlBuilderTest {

    @Test
    void singleIssueKey_producesKeyEqualsClause() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123"), null, null, null, "", "LIST", 0, null);

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("key = IB-123");
    }

    @Test
    void multipleIssueKeys_producesKeyInClause() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123", "IB-124"), null, null, null, "", "LIST", 0, null);

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("key in (IB-123, IB-124)");
    }

    @Test
    void issueKeyAndAssignee_producesCompoundClause() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123"), "jane@example.com", null, null, "", "TRANSITION", 0, "Done");

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("key = IB-123 AND assignee = \"jane@example.com\"");
    }

    @Test
    void issueKeyAndReporter_producesCompoundClause() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123"), null, "john@example.com", null, "", "TRANSITION", 0, "Done");

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("key = IB-123 AND reporter = \"john@example.com\"");
    }

    @Test
    void assigneeAndOlderThanDays_producesCompoundClause() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), "jane@example.com", null, 30, "", "LIST", 0, null);

        assertThat(AgileJqlBuilder.build(intent))
                .isEqualTo("assignee = \"jane@example.com\" AND created <= \"-30d\"");
    }

    @Test
    void allFieldsEmpty_returnsEmptyString() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "", "COUNT", 0, null);

        assertThat(AgileJqlBuilder.build(intent)).isEmpty();
    }

    @Test
    void nullIssueKeys_returnsEmptyString() {
        AgileQueryIntent intent = new AgileQueryIntent(
                null, null, null, null, null, "COUNT", 0, null);

        assertThat(AgileJqlBuilder.build(intent)).isEmpty();
    }

    @Test
    void currentUserToken_passesThoughUnquoted() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), "currentUser()", null, null, "", "LIST", 0, null);

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("assignee = currentUser()");
    }

    @Test
    void arbitraryAssigneeString_getsDoubleQuoted() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), "Jane Doe", null, null, "", "LIST", 0, null);

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("assignee = \"Jane Doe\"");
    }

    @Test
    void supplementalJqlOnly_isWrappedInParentheses() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of(), null, null, null, "status = Done", "COUNT", 0, null);

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("(status = Done)");
    }

    @Test
    void issueKeyAndSupplementalJql_combinedWithAnd() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123"), null, null, null, "status = Done", "TRANSITION", 0, "Done");

        assertThat(AgileJqlBuilder.build(intent)).isEqualTo("key = IB-123 AND (status = Done)");
    }

    @Test
    void allFieldsPopulated_allClausesCombined() {
        AgileQueryIntent intent = new AgileQueryIntent(
                List.of("IB-123"), "currentUser()", "jane@example.com", 7, "issuetype = Bug", "TRANSITION", 0, "Done");

        assertThat(AgileJqlBuilder.build(intent))
                .isEqualTo("key = IB-123 AND assignee = currentUser() AND reporter = \"jane@example.com\" AND created <= \"-7d\" AND (issuetype = Bug)");
    }
}
