package com.solesonic.agent.sports;

import com.solesonic.agent.sports.model.SportsQuestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SportsIntentClassifierTest {

    private SportsIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new SportsIntentClassifier();
    }

    @Test
    void classify_null_returnsGeneralNews() {
        List<SportsQuestionType> result = classifier.classify(null);

        assertThat(result).containsExactly(SportsQuestionType.GENERAL_NEWS);
    }

    @Test
    void classify_blank_returnsGeneralNews() {
        List<SportsQuestionType> result = classifier.classify("   ");

        assertThat(result).containsExactly(SportsQuestionType.GENERAL_NEWS);
    }

    @Test
    void classify_noMatch_returnsGeneralNews() {
        List<SportsQuestionType> result = classifier.classify("hello");

        assertThat(result).containsExactly(SportsQuestionType.GENERAL_NEWS);
    }

    @Test
    void classify_scheduleKeyword_returnsScheduleLookup() {
        List<SportsQuestionType> result = classifier.classify("when is the next game");

        assertThat(result).contains(SportsQuestionType.SCHEDULE_LOOKUP);
    }

    @Test
    void classify_standingsKeyword_returnsStandings() {
        List<SportsQuestionType> result = classifier.classify("show me the standings");

        assertThat(result).contains(SportsQuestionType.STANDINGS);
    }

    @Test
    void classify_gamePreviewKeyword_returnsGamePreview() {
        List<SportsQuestionType> result = classifier.classify("predict who wins tonight");

        assertThat(result).contains(SportsQuestionType.GAME_PREVIEW);
    }

    @Test
    void classify_playerAnalysisKeyword_returnsPlayerAnalysis() {
        List<SportsQuestionType> result = classifier.classify("how is LeBron playing this season");

        assertThat(result).contains(SportsQuestionType.PLAYER_ANALYSIS);
    }

    @Test
    void classify_statisticsKeyword_returnsStatistics() {
        List<SportsQuestionType> result = classifier.classify("show me the stats");

        assertThat(result).contains(SportsQuestionType.STATISTICS);
    }

    @Test
    void classify_tradeNewsKeyword_returnsTradeNews() {
        List<SportsQuestionType> result = classifier.classify("latest trade rumors");

        assertThat(result).contains(SportsQuestionType.TRADE_NEWS);
    }

    @Test
    void classify_injuryKeyword_returnsGeneralNews() {
        List<SportsQuestionType> result = classifier.classify("injury update for tonight");

        assertThat(result).contains(SportsQuestionType.GENERAL_NEWS);
    }

    @Test
    void classify_multipleCategories_returnsAll() {
        List<SportsQuestionType> result = classifier.classify("stats and standings this week");

        assertThat(result).contains(SportsQuestionType.STATISTICS, SportsQuestionType.STANDINGS);
    }

    @Test
    void classify_caseInsensitive_matchesKeyword() {
        List<SportsQuestionType> result = classifier.classify("STANDINGS update");

        assertThat(result).contains(SportsQuestionType.STANDINGS);
    }
}
