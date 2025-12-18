package com.solesonic.mcp.tool.general;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

@SuppressWarnings("unused")
@Service
public class DateTools {

    private static final Logger log = LoggerFactory.getLogger(DateTools.class);

    public static final String GET_CURRENT_DATE = "get_current_date";
    public static final String GET_CURRENT_TIME = "get_current_time";
    public static final String GET_CURRENT_DATE_TIME = "get_current_date_time";

    public static final String GET_CURRENT_DATE_DESC = """
            Returns the current date in ISO format (YYYY-MM-DD).
            Optionally accepts a timezone identifier (e.g., 'America/New_York', 'Europe/London').
            If no timezone is provided, defaults to UTC.
            """;

    public static final String GET_CURRENT_TIME_DESC = """
            Returns the current time in ISO format (HH:mm:ss.SSS).
            Optionally accepts a timezone identifier (e.g., 'America/New_York', 'Europe/London').
            If no timezone is provided, defaults to UTC.
            """;

    public static final String GET_CURRENT_DATE_TIME_DESC = """
            Returns the current date and time in ISO format (YYYY-MM-DDTHH:mm:ss.SSS).
            Optionally accepts a timezone identifier (e.g., 'America/New_York', 'Europe/London').
            If no timezone is provided, defaults to UTC.
            """;

    @McpTool(name = GET_CURRENT_DATE, description = GET_CURRENT_DATE_DESC)
    @PreAuthorize("hasAuthority('ROLE_MCP-TIME')")
    public DateResponse getCurrentDate(
            @McpToolParam(description = "Optional timezone identifier (e.g., 'America/New_York', 'UTC'). Defaults to UTC if not provided.")
            String timezone
    ) {
        log.info("Getting current date for timezone: {}", timezone != null ? timezone : "UTC");

        ZoneId zoneId = resolveZoneId(timezone);
        LocalDate currentDate = LocalDate.now(zoneId);

        return new DateResponse(
                currentDate.format(ISO_LOCAL_DATE),
                zoneId.getId()
        );
    }

    @McpTool(name = GET_CURRENT_TIME, description = GET_CURRENT_TIME_DESC)
    @PreAuthorize("hasAuthority('ROLE_MCP-TIME')")
    public TimeResponse getCurrentTime(
            @McpToolParam(description = "Optional timezone identifier (e.g., 'America/New_York', 'UTC'). Defaults to UTC if not provided.")
            String timezone
    ) {
        log.info("Getting current time for timezone: {}", timezone != null ? timezone : "UTC");

        ZoneId zoneId = resolveZoneId(timezone);
        LocalTime currentTime = LocalTime.now(zoneId);

        return new TimeResponse(
                currentTime.format(ISO_LOCAL_TIME),
                zoneId.getId()
        );
    }

    @McpTool(name = GET_CURRENT_DATE_TIME, description = GET_CURRENT_DATE_TIME_DESC)
    @PreAuthorize("hasAuthority('ROLE_MCP-TIME')")
    public DateTimeResponse getCurrentDateTime(
            @McpToolParam(description = "Optional timezone identifier (e.g., 'America/New_York', 'UTC'). Defaults to UTC if not provided.")
            String timezone
    ) {
        log.info("Getting current date and time for timezone: {}", timezone != null ? timezone : "UTC");

        ZoneId zoneId = resolveZoneId(timezone);
        LocalDateTime currentDateTime = LocalDateTime.now(zoneId);

        return new DateTimeResponse(
                currentDateTime.format(ISO_LOCAL_DATE_TIME),
                zoneId.getId()
        );
    }

    private ZoneId resolveZoneId(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }

        return ZoneId.of(timezone);
    }

    public record DateResponse(String date, String timezone) {}

    public record TimeResponse(String time, String timezone) {}

    public record DateTimeResponse(String dateTime, String timezone) {}
}
