package com.animegraph.identity;

/** Stable provider identity. Similar names never create identity equivalence. */
public record EntityIdentity(Kind kind, long providerId) {
    public enum Kind { CHARACTER, PERSON, ANIME }

    public EntityIdentity {
        if (kind == null || providerId < 1 || providerId > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid Bangumi entity identity");
        }
    }

    public String canonicalUid() { return kind.name().toLowerCase(java.util.Locale.ROOT) + ":bangumi:" + providerId; }
    public String sourceUrl() { return "https://bgm.tv/" + (kind == Kind.ANIME ? "subject" : kind.name().toLowerCase(java.util.Locale.ROOT)) + "/" + providerId; }
}
