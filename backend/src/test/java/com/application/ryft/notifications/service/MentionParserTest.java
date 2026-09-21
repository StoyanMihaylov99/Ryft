package com.application.ryft.notifications.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MentionParserTest {

    @Test
    void extractsASingleMentionedEmail() {
        Set<String> emails = MentionParser.extractMentionedEmails("Hey @jane@example.com can you look at this?");

        assertThat(emails).containsExactly("jane@example.com");
    }

    @Test
    void extractsMultipleDistinctMentions() {
        Set<String> emails = MentionParser.extractMentionedEmails(
                "@jane@example.com and @john@example.com should both see this");

        assertThat(emails).containsExactlyInAnyOrder("jane@example.com", "john@example.com");
    }

    @Test
    void deduplicatesRepeatedMentions() {
        Set<String> emails = MentionParser.extractMentionedEmails("@jane@example.com ping @jane@example.com again");

        assertThat(emails).containsExactly("jane@example.com");
    }

    @Test
    void lowercasesMixedCaseEmails() {
        Set<String> emails = MentionParser.extractMentionedEmails("@Jane@Example.COM take a look");

        assertThat(emails).containsExactly("jane@example.com");
    }

    @Test
    void returnsEmptySetWhenNoMentionPresent() {
        Set<String> emails = MentionParser.extractMentionedEmails("No mentions in this comment at all.");

        assertThat(emails).isEmpty();
    }

    @Test
    void ignoresAnAtSignThatIsNotShapedLikeAnEmailMention() {
        Set<String> emails = MentionParser.extractMentionedEmails("Meeting @ 3pm today, not a mention");

        assertThat(emails).isEmpty();
    }
}
