package com.solesonic.mcp.service.espn;

import com.solesonic.model.espn.EspnAthlete;
import com.solesonic.model.espn.EspnAthletePosition;
import com.solesonic.model.espn.EspnCompetition;
import com.solesonic.model.espn.EspnConference;
import com.solesonic.model.espn.EspnConferenceStandings;
import com.solesonic.model.espn.EspnEvent;
import com.solesonic.model.espn.EspnRosterResponse;
import com.solesonic.model.espn.EspnScheduleResponse;
import com.solesonic.model.espn.EspnScheduleSummary;
import com.solesonic.model.espn.EspnStandingStat;
import com.solesonic.model.espn.EspnStandingsEntry;
import com.solesonic.model.espn.EspnStandingsResponse;
import com.solesonic.model.espn.EspnStatus;
import com.solesonic.model.espn.EspnStatusType;
import com.solesonic.model.espn.EspnTeamInfo;
import com.solesonic.service.espn.EspnClient;
import com.solesonic.service.espn.EspnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EspnServiceTest {

    @Mock
    private EspnClient espnClient;

    private EspnService service;

    @BeforeEach
    void setUp() {
        service = new EspnService(espnClient);
    }

    // --- getScheduleSummary ---

    @Test
    void getScheduleSummary_nullAbbreviations_delegatesToScoreboard() {
        EspnEvent event = upcomingEvent("1", "2025-01-01T19:00:00Z");
        when(espnClient.fetchScoreboard()).thenReturn(new EspnScheduleResponse(List.of(event)));

        EspnScheduleSummary result = service.getScheduleSummary(null);

        assertThat(result.games()).hasSize(1);
    }

    @Test
    void getScheduleSummary_emptyAbbreviations_delegatesToScoreboard() {
        EspnEvent event = upcomingEvent("1", "2025-01-01T19:00:00Z");
        when(espnClient.fetchScoreboard()).thenReturn(new EspnScheduleResponse(List.of(event)));

        EspnScheduleSummary result = service.getScheduleSummary(List.of());

        assertThat(result.games()).hasSize(1);
    }

    @Test
    void getScheduleSummary_withTeams_deduplicatesEvents() {
        EspnEvent sharedEvent = upcomingEvent("shared-id", "2025-01-01T19:00:00Z");
        when(espnClient.fetchTeamSchedule("bos")).thenReturn(new EspnScheduleResponse(List.of(sharedEvent)));
        when(espnClient.fetchTeamSchedule("lal")).thenReturn(new EspnScheduleResponse(List.of(sharedEvent)));

        EspnScheduleSummary result = service.getScheduleSummary(List.of("bos", "lal"));

        assertThat(result.games()).hasSize(1);
    }

    @Test
    void getScheduleSummary_withTeams_filtersCompletedEvents() {
        EspnEvent completedEvent = completedEvent("done-id", "2025-01-01T19:00:00Z");
        when(espnClient.fetchTeamSchedule("bos")).thenReturn(new EspnScheduleResponse(List.of(completedEvent)));

        EspnScheduleSummary result = service.getScheduleSummary(List.of("bos"));

        assertThat(result.games()).isEmpty();
    }

    @Test
    void getScheduleSummary_withTeams_sortsByDate() {
        EspnEvent laterEvent = upcomingEvent("2", "2025-02-01T19:00:00Z");
        EspnEvent earlierEvent = upcomingEvent("1", "2025-01-01T19:00:00Z");
        when(espnClient.fetchTeamSchedule("bos"))
                .thenReturn(new EspnScheduleResponse(List.of(laterEvent, earlierEvent)));

        EspnScheduleSummary result = service.getScheduleSummary(List.of("bos"));

        assertThat(result.games()).extracting(EspnEvent::date)
                .containsExactly("2025-01-01T19:00:00Z", "2025-02-01T19:00:00Z");
    }

    @Test
    void getScheduleSummary_nullScoreboardResponse_returnsEmptySummary() {
        when(espnClient.fetchScoreboard()).thenReturn(null);

        EspnScheduleSummary result = service.getScheduleSummary(null);

        assertThat(result.games()).isEmpty();
    }

    // --- getRosterData ---

    @Test
    void getRosterData_null_returnsNoDataMessage() {
        String result = service.getRosterData(null);

        assertThat(result).isEqualTo("No team roster data available.");
    }

    @Test
    void getRosterData_emptyList_returnsNoDataMessage() {
        String result = service.getRosterData(List.of());

        assertThat(result).isEqualTo("No team roster data available.");
    }

    @Test
    void getRosterData_nullApiResponse_includesFallbackLine() {
        when(espnClient.fetchTeamRoster("bos")).thenReturn(null);

        String result = service.getRosterData(List.of("bos"));

        assertThat(result).contains("No roster data available for BOS");
    }

    @Test
    void getRosterData_validResponse_formatsAthletesCorrectly() {
        EspnAthletePosition position = new EspnAthletePosition("G", "Guard");
        EspnAthlete athlete = new EspnAthlete("Jayson Tatum", "0", position);
        when(espnClient.fetchTeamRoster("bos")).thenReturn(new EspnRosterResponse(List.of(athlete)));

        String result = service.getRosterData(List.of("bos"));

        assertThat(result).contains("#0").contains("Jayson Tatum").contains("(G)");
    }

    // --- getStandingsData ---

    @Test
    void getStandingsData_nullResponse_returnsNoDataMessage() {
        when(espnClient.fetchStandings()).thenReturn(null);

        String result = service.getStandingsData();

        assertThat(result).isEqualTo("No standings data available.");
    }

    @Test
    void getStandingsData_validResponse_formatsConferencesAndTeams() {
        List<EspnStandingStat> stats = List.of(
                new EspnStandingStat("wins", "30"),
                new EspnStandingStat("losses", "10"),
                new EspnStandingStat("winPercent", ".750")
        );
        EspnTeamInfo team = new EspnTeamInfo("1", "BOS", "Boston Celtics");
        EspnStandingsEntry entry = new EspnStandingsEntry(team, stats);
        EspnConference conference = new EspnConference(
                "Eastern Conference", "east", new EspnConferenceStandings(List.of(entry)));
        when(espnClient.fetchStandings()).thenReturn(new EspnStandingsResponse(List.of(conference)));

        String result = service.getStandingsData();

        assertThat(result)
                .contains("Eastern Conference")
                .contains("Boston Celtics")
                .contains("30")
                .contains("10")
                .contains(".750");
    }

    @Test
    void getStandingsData_missingStat_showsQuestionMark() {
        EspnTeamInfo team = new EspnTeamInfo("1", "LAL", "Los Angeles Lakers");
        EspnStandingsEntry entry = new EspnStandingsEntry(team, List.of());
        EspnConference conference = new EspnConference(
                "Western Conference", "west", new EspnConferenceStandings(List.of(entry)));
        when(espnClient.fetchStandings()).thenReturn(new EspnStandingsResponse(List.of(conference)));

        String result = service.getStandingsData();

        assertThat(result).contains("Los Angeles Lakers").contains("?");
    }

    // --- factory helpers ---

    private EspnEvent upcomingEvent(String id, String date) {
        EspnStatusType statusType = new EspnStatusType("pre", "Scheduled", "7:00 PM ET");
        EspnStatus status = new EspnStatus(statusType);
        EspnCompetition competition = new EspnCompetition(List.of(), List.of(), List.of(), status);
        return new EspnEvent(id, date, "Game", List.of(competition), status);
    }

    private EspnEvent completedEvent(String id, String date) {
        EspnStatusType statusType = new EspnStatusType("post", "Final", "Final");
        EspnStatus status = new EspnStatus(statusType);
        EspnCompetition competition = new EspnCompetition(List.of(), List.of(), List.of(), status);
        return new EspnEvent(id, date, "Game", List.of(competition), status);
    }
}
