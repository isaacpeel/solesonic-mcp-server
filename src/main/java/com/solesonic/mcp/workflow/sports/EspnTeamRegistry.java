package com.solesonic.mcp.workflow.sports;

import com.solesonic.mcp.workflow.sports.model.EspnTeamProfile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative mapping of all 30 NBA teams to their ESPN URL profiles.
 * Keyed by full team name and common aliases — all lowercase for case-insensitive lookup.
 * <p>
 * ESPN URL pattern: <a href="https://www.espn.com/nba/team/">...</a>{section}/_/name/{abbreviation}/{slug}
 * Reference: <a href="https://www.espn.com/nba/teams">...</a>
 */
@Component
public class EspnTeamRegistry {

    private static final Map<String, EspnTeamProfile> PROFILES_BY_NAME = new HashMap<>();

    static {
        register("Atlanta Hawks",            "atl",  "atlanta-hawks",
                "hawks", "atl");
        register("Boston Celtics",           "bos",  "boston-celtics",
                "celtics", "c's", "bos");
        register("Brooklyn Nets",            "bkn",  "brooklyn-nets",
                "nets", "brooklyn", "bkn");
        register("Charlotte Hornets",        "cha",  "charlotte-hornets",
                "hornets", "cha");
        register("Chicago Bulls",            "chi",  "chicago-bulls",
                "bulls", "chi");
        register("Cleveland Cavaliers",      "cle",  "cleveland-cavaliers",
                "cavaliers", "cavs", "cle");
        register("Dallas Mavericks",         "dal",  "dallas-mavericks",
                "mavericks", "mavs", "dal");
        register("Denver Nuggets",           "den",  "denver-nuggets",
                "nuggets", "den");
        register("Detroit Pistons",          "det",  "detroit-pistons",
                "pistons", "det");
        register("Golden State Warriors",    "gs",   "golden-state-warriors",
                "warriors", "dubs", "gsw", "golden state");
        register("Houston Rockets",          "hou",  "houston-rockets",
                "rockets", "hou");
        register("Indiana Pacers",           "ind",  "indiana-pacers",
                "pacers", "ind");
        register("Los Angeles Clippers",     "lac",  "la-clippers",
                "clippers", "lac", "la clippers");
        register("Los Angeles Lakers",       "lal",  "los-angeles-lakers",
                "lakers", "lal", "la lakers");
        register("Memphis Grizzlies",        "mem",  "memphis-grizzlies",
                "grizzlies", "grizz", "mem");
        register("Miami Heat",               "mia",  "miami-heat",
                "heat", "mia");
        register("Milwaukee Bucks",          "mil",  "milwaukee-bucks",
                "bucks", "mil");
        register("Minnesota Timberwolves",   "min",  "minnesota-timberwolves",
                "timberwolves", "wolves", "minny", "min");
        register("New Orleans Pelicans",     "no",   "new-orleans-pelicans",
                "pelicans", "new orleans", "no");
        register("New York Knicks",          "ny",   "new-york-knicks",
                "knicks", "ny", "nyc");
        register("Oklahoma City Thunder",    "okc",  "oklahoma-city-thunder",
                "thunder", "okc", "oklahoma city");
        register("Orlando Magic",            "orl",  "orlando-magic",
                "magic", "orlando", "orl");
        register("Philadelphia 76ers",       "phi",  "philadelphia-76ers",
                "76ers", "sixers", "philly", "phi");
        register("Phoenix Suns",             "phx",  "phoenix-suns",
                "suns", "phx");
        register("Portland Trail Blazers",   "por",  "portland-trail-blazers",
                "trail blazers", "blazers", "portland", "por");
        register("Sacramento Kings",         "sac",  "sacramento-kings",
                "kings", "sacramento", "sac");
        register("San Antonio Spurs",        "sa",   "san-antonio-spurs",
                "spurs", "san antonio", "sa");
        register("Toronto Raptors",          "tor",  "toronto-raptors",
                "raptors", "toronto", "tor");
        register("Utah Jazz",                "utah", "utah-jazz",
                "jazz", "utah");
        register("Washington Wizards",       "wsh",  "washington-wizards",
                "wizards", "washington", "wsh");
    }

    private static void register(String fullName, String abbreviation, String slug, String... aliases) {
        EspnTeamProfile profile = EspnTeamProfile.of(fullName, abbreviation, slug);
        PROFILES_BY_NAME.put(fullName.toLowerCase(), profile);
        for (String alias : aliases) {
            PROFILES_BY_NAME.put(alias.toLowerCase(), profile);
        }
    }

    /**
     * Looks up a team by any recognized name or alias (case-insensitive).
     * Returns empty if the name is not recognized.
     */
    public Optional<EspnTeamProfile> findByName(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PROFILES_BY_NAME.get(teamName.toLowerCase().trim()));
    }
}
