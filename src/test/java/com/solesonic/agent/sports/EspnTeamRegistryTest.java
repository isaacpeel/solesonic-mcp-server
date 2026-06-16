package com.solesonic.agent.sports;

import com.solesonic.agent.sports.model.EspnTeamProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EspnTeamRegistryTest {

    private EspnTeamRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EspnTeamRegistry();
    }

    @Test
    void findByName_fullName_returnsProfile() {
        Optional<EspnTeamProfile> result = registry.findByName("Boston Celtics");

        assertThat(result).isPresent();
        assertThat(result.get().abbreviation()).isEqualTo("bos");
        assertThat(result.get().urlSlug()).isEqualTo("boston-celtics");
        assertThat(result.get().fullName()).isEqualTo("Boston Celtics");
    }

    @Test
    void findByName_caseInsensitive_returnsProfile() {
        assertThat(registry.findByName("boston celtics")).isPresent();
        assertThat(registry.findByName("BOSTON CELTICS")).isPresent();
    }

    @Test
    void findByName_commonAlias_returnsProfile() {
        assertThat(registry.findByName("celtics").map(EspnTeamProfile::fullName))
                .contains("Boston Celtics");

        assertThat(registry.findByName("c's").map(EspnTeamProfile::fullName))
                .contains("Boston Celtics");

        assertThat(registry.findByName("lakers").map(EspnTeamProfile::fullName))
                .contains("Los Angeles Lakers");

        assertThat(registry.findByName("dubs").map(EspnTeamProfile::fullName))
                .contains("Golden State Warriors");
    }

    @Test
    void findByName_abbreviation_returnsProfile() {
        assertThat(registry.findByName("lal").map(EspnTeamProfile::fullName))
                .contains("Los Angeles Lakers");

        assertThat(registry.findByName("bos").map(EspnTeamProfile::fullName))
                .contains("Boston Celtics");

        assertThat(registry.findByName("gsw").map(EspnTeamProfile::fullName))
                .contains("Golden State Warriors");
    }

    @Test
    void findByName_trailingWhitespace_returnsProfile() {
        assertThat(registry.findByName("  lakers  ")).isPresent();
    }

    @Test
    void findByName_null_returnsEmpty() {
        assertThat(registry.findByName(null)).isEmpty();
    }

    @Test
    void findByName_blank_returnsEmpty() {
        assertThat(registry.findByName("   ")).isEmpty();
    }

    @Test
    void findByName_unknown_returnsEmpty() {
        assertThat(registry.findByName("Barcelona")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Atlanta Hawks", "Boston Celtics", "Brooklyn Nets", "Charlotte Hornets",
        "Chicago Bulls", "Cleveland Cavaliers", "Dallas Mavericks", "Denver Nuggets",
        "Detroit Pistons", "Golden State Warriors", "Houston Rockets", "Indiana Pacers",
        "Los Angeles Clippers", "Los Angeles Lakers", "Memphis Grizzlies", "Miami Heat",
        "Milwaukee Bucks", "Minnesota Timberwolves", "New Orleans Pelicans", "New York Knicks",
        "Oklahoma City Thunder", "Orlando Magic", "Philadelphia 76ers", "Phoenix Suns",
        "Portland Trail Blazers", "Sacramento Kings", "San Antonio Spurs", "Toronto Raptors",
        "Utah Jazz", "Washington Wizards"
    })
    void findByName_allThirtyTeams_resolveByFullName(String fullName) {
        Optional<EspnTeamProfile> result = registry.findByName(fullName);

        assertThat(result).isPresent();
        assertThat(result.get().fullName()).isNotNull();
        assertThat(result.get().abbreviation()).isNotNull();
        assertThat(result.get().urlSlug()).isNotNull();
    }
}
