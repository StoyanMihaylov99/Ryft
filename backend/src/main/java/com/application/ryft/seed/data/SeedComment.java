package com.application.ryft.seed.data;

/**
 * {@code body} may embed an {@code @email@domain} mention — {@code notifications.service.MentionParser}
 * matches on the literal email, not a display name, so a mention here must spell out the target user's
 * seed email (e.g. {@code "cc @bob@ryft.dev"}) to actually trigger a MENTION notification.
 */
public record SeedComment(
        String authorEmail,
        String body
) {
}
