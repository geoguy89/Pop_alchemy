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
