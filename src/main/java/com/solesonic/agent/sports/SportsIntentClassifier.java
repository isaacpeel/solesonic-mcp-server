package com.solesonic.agent.sports;

import com.solesonic.agent.sports.model.SportsQuestionType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Keyword-based classifier that maps a user's NBA question to one or more SportsQuestionType values
 * without calling an LLM. Covers the large majority of real user questions in under 1ms.
 * <p>
 * Falls back to GENERAL_NEWS when no keywords match so that downstream routing always has
 * a valid intent type.
 */
@Component
public class SportsIntentClassifier {

    private static final Set<String> SCHEDULE_KEYWORDS = Set.of(
            "when", "what time", "game time", "schedule", "channel", "tv", "broadcast",
            "tip-off", "tipoff", "start time", "next game", "air", "playing tonight",
            "on tonight", "on tv", "streaming", "watch", "tip off"
    );

    private static final Set<String> STANDINGS_KEYWORDS = Set.of(
            "standings", "ranking", "ranked", "win-loss", "win loss", "record",
            "playoff picture", "conference", "seed", "seeded", "elimination number",
            "eliminated", "clinched", "first place", "last place", "in the playoffs",
            "make the playoffs", "playoff race"
    );

    private static final Set<String> GAME_PREVIEW_KEYWORDS = Set.of(
            "preview", "matchup", "match up", "predict", "prediction", "starting lineup",
            "starting five", "starters", "who starts", "who will start", "going to start",
            "start tonight", "start the game", "start for", "starting for",
            "who wins", "who will win", "analysis",
            "breakdown", "advantage", "series", "playoff series", "double-double",
            "triple-double", "game plan", "head to head", "head-to-head", "favorite",
            "odds", "spread", "over under", "over/under", "box score", "keys to"
    );

    private static final Set<String> PLAYER_ANALYSIS_KEYWORDS = Set.of(
            "ppg", "rpg", "apg", "per game", "averaging", "how is", "how has",
            "playing well", "playing poorly", "in a slump", "on fire", "hot streak",
            "career high", "career-high", "his numbers", "her numbers", "their numbers",
            "minutes", "playing time", "rotation", "bench"
    );

    private static final Set<String> STATISTICS_KEYWORDS = Set.of(
            "stats", "statistics", "numbers", "shooting percentage", "fg%", "3p%", "ft%",
            "field goal", "three point", "free throw", "rebounds", "assists", "blocks",
            "steals", "turnovers", "plus minus", "plus/minus", "+/-", "leaders",
            "league leaders", "efficiency", "rating", "per", "usage", "true shooting",
            "ts%", "net rating", "offensive rating", "defensive rating"
    );

    private static final Set<String> TRADE_NEWS_KEYWORDS = Set.of(
            "trade", "traded", "rumor", "rumours", "rumors", "waived", "signed",
            "signing", "transaction", "free agent", "buyout", "acquired", "roster move",
            "cut", "released", "extension", "contract", "salary", "cap space"
    );

    private static final Set<String> GENERAL_NEWS_KEYWORDS = Set.of(
            "news", "update", "latest", "report", "interview", "press conference",
            "statement", "tweet", "suspension", "suspended", "ejected", "fined"
    );

    private static final Set<String> INJURY_REPORT_KEYWORDS = Set.of(
            "injury", "injured", "hurt", "status", "health", "questionable", "doubtful",
            "out", "day-to-day", "return", "timeline", "availability", "sidelined",
            "missed", "listed", "pain", "knee", "ankle", "shoulder", "back", "hamstring",
            "concussion", "sprain", "strain", "swelling", "load management"
    );

    private static final Set<String> HISTORICAL_KEYWORDS = Set.of(
            "all time", "all-time", "career", "history", "historical", "record",
            "greatest", "best ever", "most", "season high", "season-high",
            "franchise record", "championship", "title", "finals", "mvp",
            "hall of fame", "legacy", "era", "decade", "compare"
    );

    private static final Set<String> DRAFT_KEYWORDS = Set.of(
            "draft", "drafted", "prospect", "lottery", "pick", "combine",
            "mock draft", "scouting", "recruit", "class", "g league"
    );

    private static final Set<String> COACHING_KEYWORDS = Set.of(
            "coach", "coaching", "head coach", "assistant coach", "fired", "hired",
            "strategy", "system", "rotation", "lineup decision", "play style",
            "offensive system", "defensive scheme", "timeout", "adjustment"
    );

    public List<SportsQuestionType> classify(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of(SportsQuestionType.GENERAL_NEWS);
        }

        String lowercaseMessage = userMessage.toLowerCase();
        List<SportsQuestionType> matches = new ArrayList<>();

        if (matchesAny(lowercaseMessage, SCHEDULE_KEYWORDS)) {
            matches.add(SportsQuestionType.SCHEDULE_LOOKUP);
        }

        if (matchesAny(lowercaseMessage, STANDINGS_KEYWORDS)) {
            matches.add(SportsQuestionType.STANDINGS);
        }

        if (matchesAny(lowercaseMessage, GAME_PREVIEW_KEYWORDS)) {
            matches.add(SportsQuestionType.GAME_PREVIEW);
        }

        if (matchesAny(lowercaseMessage, PLAYER_ANALYSIS_KEYWORDS)) {
            matches.add(SportsQuestionType.PLAYER_ANALYSIS);
        }

        if (matchesAny(lowercaseMessage, STATISTICS_KEYWORDS)) {
            matches.add(SportsQuestionType.STATISTICS);
        }

        if (matchesAny(lowercaseMessage, TRADE_NEWS_KEYWORDS)) {
            matches.add(SportsQuestionType.TRADE_NEWS);
        }

        if (matchesAny(lowercaseMessage, INJURY_REPORT_KEYWORDS)) {
            matches.add(SportsQuestionType.INJURY_REPORT);
        }

        if (matchesAny(lowercaseMessage, HISTORICAL_KEYWORDS)) {
            matches.add(SportsQuestionType.HISTORICAL);
        }

        if (matchesAny(lowercaseMessage, DRAFT_KEYWORDS)) {
            matches.add(SportsQuestionType.DRAFT);
        }

        if (matchesAny(lowercaseMessage, COACHING_KEYWORDS)) {
            matches.add(SportsQuestionType.COACHING);
        }

        if (matchesAny(lowercaseMessage, GENERAL_NEWS_KEYWORDS)) {
            matches.add(SportsQuestionType.GENERAL_NEWS);
        }

        if (matches.isEmpty()) {
            matches.add(SportsQuestionType.GENERAL_NEWS);
        }

        return List.copyOf(matches);
    }

    private boolean matchesAny(String lowercaseMessage, Set<String> keywords) {
        for (String keyword : keywords) {
            if (keyword.contains(" ") || keyword.contains("-")) {
                if (lowercaseMessage.contains(keyword)) {
                    return true;
                }
            } else {
                int index = lowercaseMessage.indexOf(keyword);
                while (index >= 0) {
                    boolean prefixBoundary = index == 0
                            || !Character.isLetterOrDigit(lowercaseMessage.charAt(index - 1));
                    boolean suffixBoundary = index + keyword.length() == lowercaseMessage.length()
                            || !Character.isLetterOrDigit(lowercaseMessage.charAt(index + keyword.length()));
                    if (prefixBoundary && suffixBoundary) {
                        return true;
                    }
                    index = lowercaseMessage.indexOf(keyword, index + 1);
                }
            }
        }
        return false;
    }
}
