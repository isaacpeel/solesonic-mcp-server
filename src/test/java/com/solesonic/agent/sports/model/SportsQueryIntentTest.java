package com.solesonic.agent.sports.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SportsQueryIntentTest {

    @Test
    void hasTeams_emptyList_returnsFalse() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of(), "today");

        assertThat(intent.hasTeams()).isFalse();
    }

    @Test
    void hasTeams_nonEmptyList_returnsTrue() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of("Lakers"), List.of(), "today");

        assertThat(intent.hasTeams()).isTrue();
    }

    @Test
    void hasPlayers_emptyList_returnsFalse() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of(), "today");

        assertThat(intent.hasPlayers()).isFalse();
    }

    @Test
    void hasPlayers_nonEmptyList_returnsTrue() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of("LeBron"), "today");

        assertThat(intent.hasPlayers()).isTrue();
    }

    @Test
    void hasFocusPlayer_singlePlayer_returnsTrue() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of("LeBron"), "today");

        assertThat(intent.hasFocusPlayer()).isTrue();
    }

    @Test
    void hasFocusPlayer_noPlayers_returnsFalse() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of(), "today");

        assertThat(intent.hasFocusPlayer()).isFalse();
    }

    @Test
    void hasFocusPlayer_multiplePlayers_returnsFalse() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of("LeBron", "AD"), "today");

        assertThat(intent.hasFocusPlayer()).isFalse();
    }

    @Test
    void focusPlayer_singlePlayer_returnsName() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of("LeBron"), "today");

        assertThat(intent.focusPlayer()).isEqualTo("LeBron");
    }

    @Test
    void focusPlayer_noPlayers_returnsNull() {
        SportsQueryIntent intent = new SportsQueryIntent(List.of(), List.of(), List.of(), "today");

        assertThat(intent.focusPlayer()).isNull();
    }
}
