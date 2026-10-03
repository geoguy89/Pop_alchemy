package com.geoguy89.refinersfire

/** The installed version, shown in Options. Set by each app at startup. */
object AppVersion {
    var name: String = "dev"
    var build: Int = 0
    val label: String get() = if (build > 0) "Version $name (build $build)" else "Version $name"
}

/** Wall-clock time, for dating Hall of Fame entries. */
expect fun epochMillis(): Long

/** A short, locale-appropriate date. */
expect fun formatDate(epochMillis: Long): String

/** The player's local date as a day number (days since 1 January 1970). */
expect fun localDay(epochMillis: Long): Long

/** A day number as a long date, e.g. "Saturday, October 3". */
expect fun formatDay(day: Long): String

/** Cryptographically secure random bytes (chat keys and nonces). */
expect fun secureRandomBytes(n: Int): ByteArray

/** True in the web version (iPhone, iPad, any browser). */
expect val isWeb: Boolean
