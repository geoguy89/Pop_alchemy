package com.geoguy89.refinersfire

import java.text.DateFormat
import java.util.Date

actual fun epochMillis(): Long = System.currentTimeMillis()

actual fun formatDate(epochMillis: Long): String = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(epochMillis))
