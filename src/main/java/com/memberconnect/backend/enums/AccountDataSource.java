package com.memberconnect.backend.enums;

/**
 * Where an operative account's details came from.
 *
 * Until the Finance Module is connected these rows are typed in by hand. Stamping
 * the origin means that once Finance starts syncing, anything still MANUAL is
 * visibly un-migrated rather than silently stale.
 */
public enum AccountDataSource {
    MANUAL,
    FINANCE
}
