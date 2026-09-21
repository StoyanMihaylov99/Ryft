package com.application.ryft.notifications.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure static utility, not a Spring bean — {@code @mention} detection has no state and no dependency
 * worth injecting. Package-private: only {@link NotificationServiceImpl} needs it.
 */
final class MentionParser {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,})");

    private MentionParser() {
    }

    /** Lowercased, de-duplicated emails mentioned via {@code @user@example.com} in the given comment body. */
    static Set<String> extractMentionedEmails(String commentBody) {
        Set<String> emails = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(commentBody);
        while (matcher.find()) {
            emails.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return emails;
    }
}
