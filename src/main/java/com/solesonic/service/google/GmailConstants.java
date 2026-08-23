package com.solesonic.service.google;

public class GmailConstants {
    public static final String GMAIL_PATH = "gmail";
    public static final String VERSION_PATH = "v1";
    public static final String USERS_PATH = "users";
    public static final String MESSAGES_PATH = "messages";

    /** Gmail's alias for "whoever owns the access token". */
    public static final String ME = "me";

    public static final String LABEL_IDS_PARAM = "labelIds";
    public static final String MAX_RESULTS_PARAM = "maxResults";
    public static final String FORMAT_PARAM = "format";
    public static final String METADATA_HEADERS_PARAM = "metadataHeaders";

    public static final String INBOX_LABEL = "INBOX";
    public static final String METADATA_FORMAT = "metadata";

    public static final String SUBJECT_HEADER = "Subject";
    public static final String FROM_HEADER = "From";
    public static final String DATE_HEADER = "Date";

    public static final String NO_SUBJECT = "(no subject)";
    public static final String UNKNOWN_SENDER = "(unknown sender)";

    private GmailConstants() {
    }
}
