package com.solesonic.mcp.tool.general;

import com.solesonic.mcp.tool.general.DateTools.DateResponse;
import com.solesonic.mcp.tool.general.DateTools.DateTimeResponse;
import com.solesonic.mcp.tool.general.DateTools.TimeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DateToolsTest {

    private DateTools dateTools;

    @BeforeEach
    void setUp() {
        dateTools = new DateTools();
    }

    @Test
    void getCurrentDateWithNullTimezoneReturnsUtc() {
        DateResponse response = dateTools.getCurrentDate(null);

        assertNotNull(response);
        assertNotNull(response.date());
        assertEquals("UTC", response.timezone());
        assertDoesNotThrow(() -> LocalDate.parse(response.date(), ISO_LOCAL_DATE));
    }

    @Test
    void getCurrentDateWithBlankTimezoneReturnsUtc() {
        DateResponse response = dateTools.getCurrentDate("   ");

        assertNotNull(response);
        assertNotNull(response.date());
        assertEquals("UTC", response.timezone());
        assertDoesNotThrow(() -> LocalDate.parse(response.date(), ISO_LOCAL_DATE));
    }

    @Test
    void getCurrentDateWithSpecificTimezoneReturnsCorrectTimezone() {
        String timezone = "America/New_York";
        DateResponse response = dateTools.getCurrentDate(timezone);

        assertNotNull(response);
        assertNotNull(response.date());
        assertEquals(timezone, response.timezone());
        assertDoesNotThrow(() -> LocalDate.parse(response.date(), ISO_LOCAL_DATE));
    }

    @Test
    void getCurrentDateWithUtcTimezoneReturnsUtc() {
        String timezone = "UTC";
        DateResponse response = dateTools.getCurrentDate(timezone);

        assertNotNull(response);
        assertNotNull(response.date());
        assertEquals(timezone, response.timezone());
        assertDoesNotThrow(() -> LocalDate.parse(response.date(), ISO_LOCAL_DATE));
    }

    @Test
    void getCurrentDateWithInvalidTimezoneThrowsException() {
        assertThrows(Exception.class, () -> dateTools.getCurrentDate("Invalid/Timezone"));
    }

    @Test
    void getCurrentTimeWithNullTimezoneReturnsUtc() {
        TimeResponse response = dateTools.getCurrentTime(null);

        assertNotNull(response);
        assertNotNull(response.time());
        assertEquals("UTC", response.timezone());
        assertDoesNotThrow(() -> LocalTime.parse(response.time(), ISO_LOCAL_TIME));
    }

    @Test
    void getCurrentTimeWithBlankTimezoneReturnsUtc() {
        TimeResponse response = dateTools.getCurrentTime("");

        assertNotNull(response);
        assertNotNull(response.time());
        assertEquals("UTC", response.timezone());
        assertDoesNotThrow(() -> LocalTime.parse(response.time(), ISO_LOCAL_TIME));
    }

    @Test
    void getCurrentTimeWithSpecificTimezoneReturnsCorrectTimezone() {
        String timezone = "Europe/London";
        TimeResponse response = dateTools.getCurrentTime(timezone);

        assertNotNull(response);
        assertNotNull(response.time());
        assertEquals(timezone, response.timezone());
        assertDoesNotThrow(() -> LocalTime.parse(response.time(), ISO_LOCAL_TIME));
    }

    @Test
    void getCurrentTimeWithInvalidTimezoneThrowsException() {
        assertThrows(Exception.class, () -> dateTools.getCurrentTime("Not/A/Timezone"));
    }

    @Test
    void getCurrentDateTimeWithNullTimezoneReturnsUtc() {
        DateTimeResponse response = dateTools.getCurrentDateTime(null);

        assertNotNull(response);
        assertNotNull(response.dateTime());
        assertEquals("UTC", response.timezone());
        assertDoesNotThrow(() -> LocalDateTime.parse(response.dateTime(), ISO_LOCAL_DATE_TIME));
    }

    @Test
    void getCurrentDateTimeWithBlankTimezoneReturnsUtc() {
        DateTimeResponse response = dateTools.getCurrentDateTime("  ");

        assertNotNull(response);
        assertNotNull(response.dateTime());
        assertEquals("UTC", response.timezone());
        assertDoesNotThrow(() -> LocalDateTime.parse(response.dateTime(), ISO_LOCAL_DATE_TIME));
    }

    @Test
    void getCurrentDateTimeWithSpecificTimezoneReturnsCorrectTimezone() {
        String timezone = "Asia/Tokyo";
        DateTimeResponse response = dateTools.getCurrentDateTime(timezone);

        assertNotNull(response);
        assertNotNull(response.dateTime());
        assertEquals(timezone, response.timezone());
        assertDoesNotThrow(() -> LocalDateTime.parse(response.dateTime(), ISO_LOCAL_DATE_TIME));
    }

    @Test
    void getCurrentDateTimeWithUtcTimezoneReturnsUtc() {
        String timezone = "UTC";
        DateTimeResponse response = dateTools.getCurrentDateTime(timezone);

        assertNotNull(response);
        assertNotNull(response.dateTime());
        assertEquals(timezone, response.timezone());
        assertDoesNotThrow(() -> LocalDateTime.parse(response.dateTime(), ISO_LOCAL_DATE_TIME));
    }

    @Test
    void getCurrentDateTimeWithInvalidTimezoneThrowsException() {
        assertThrows(Exception.class, () -> dateTools.getCurrentDateTime("Fake/Zone"));
    }
}
