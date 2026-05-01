package com.solesonic.mcp.prompt;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class PromptConstants {
    public static final String USER_MESSAGE = "userMessage";
    public static final String TODAY_DATE = "todayDate";
    public static final String QUESTION_TYPE = "questionType";
    public static final String SCHEDULE_RESULTS = "scheduleResults";
    public static final String NEWS_RESULTS = "newsResults";
    public static final String STATS_RESULTS = "statsResults";
    public static final String NBA_TERMINOLOGY = "nbaTerminology";

    public static final String NBA_TERMINOLOGY_CONTENT = """
            NBA TERMINOLOGY REFERENCE:
              Double-double    — A player achieves double digits (10+) in two major statistical categories \
            in a single game (e.g., 20 points and 11 rebounds).
              Triple-double    — A player achieves double digits (10+) in three major statistical categories \
            in a single game (e.g., 25 points, 10 rebounds, and 10 assists).
              Quadruple-double — Extremely rare; double digits in four statistical categories in a single game.
              Assist           — A pass that directly leads to a made basket by a teammate.
              Turnover         — Loss of ball possession without taking a shot (e.g., bad pass, travel).
              Steal            — Taking the ball away from an opposing player, causing a change of possession.
              Block            — Legally deflecting an opponent's shot attempt.
              Rebound          — Recovering the ball after a missed shot. Offensive rebound: your own team missed; \
            defensive rebound: the opponent missed.
              Paint / The Lane — The rectangular painted area under each basket (also called the key or lane).
              Pick and roll    — An offensive play where one player sets a screen (pick) for the ball-handler, \
            then cuts toward the basket (rolls) to receive a pass.
              Iso / Isolation  — A one-on-one offensive play where the ball-handler takes on a defender \
            without screens, relying on individual skill.
              Fast break       — An offensive push where the team advances quickly upcourt before the defense \
            can set, often producing easy baskets.
              Sixth man        — The first player off the bench; often a key scorer or playmaker despite not starting.
              Plus/minus (+/-) — The team's scoring margin (points scored minus points allowed) while \
            a specific player is on the court.
              FG% / 3P% / FT% — Field goal percentage, three-point percentage, and free throw percentage: \
            shooting accuracy metrics by zone or situation.
              True Shooting % (TS%) — A comprehensive shooting efficiency metric accounting for field goals, \
            three-pointers, and free throws.
              PER              — Player Efficiency Rating: a per-minute measure of overall statistical contributions, \
            normalized to a league average of 15.
              Usage rate       — The percentage of team possessions a player uses while on the court \
            (via field goal attempts, free throw attempts, or turnovers).
              Net rating       — Point differential per 100 possessions (offensive rating minus defensive rating); \
            higher is better.
              Flagrant foul    — An excessive or violent foul; may award free throws, possession, and possible \
            ejection (Flagrant 1 vs. Flagrant 2).
              Technical foul   — A foul for unsportsmanlike conduct not involving physical player contact \
            (e.g., arguing with officials, taunting); results in a free throw for the opponent.
            """;

    public static String todayDate() {
        ZonedDateTime easternTime = ZonedDateTime.now(ZoneId.of("America/New_York"));

        // Pattern including time and zone (e.g., 2026-04-30 21:31:00 EST)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

        return easternTime.format(formatter);
    }
}
