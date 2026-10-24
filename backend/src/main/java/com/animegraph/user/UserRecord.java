package com.animegraph.user;

import java.time.Instant;

public record UserRecord(long id, String username, String passwordHash, String displayName,
                         boolean enabled, int authVersion, Instant createdAt) {}
