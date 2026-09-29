package com.geoguy89.refinersfire.data

import java.io.File
import java.util.Properties

/** Stores values in a properties file, written atomically on every change. */
class FileKeyValueStore(private val file: File) : KeyValueStore {
    private val props = Properties()

    init {
        try {
            if (file.exists()) file.inputStream().use { props.load(it) }
        } catch (_: Exception) {
            // A corrupt file just means a fresh start.
        }
    }

    override fun get(key: String): String? = synchronized(props) { props.getProperty(key) }

    override fun put(key: String, value: String?) = synchronized(props) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.outputStream().use { props.store(it, "Refiner's Fire") }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        /** %APPDATA%\RefinersFire on Windows, ~/Library/Application Support/RefinersFire on macOS, ~/.local/share/refinersfire elsewhere. */
        fun default(): FileKeyValueStore {
            val dir = dataDir("RefinersFire", "refinersfire")
            return FileKeyValueStore(File(dir, "refinersfire.properties"))
        }

        private fun dataDir(name: String, lower: String): File {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            return when {
                os.contains("win") -> File(System.getenv("APPDATA") ?: home, name)
                os.contains("mac") -> File(home, "Library/Application Support/$name")
                else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", lower)
            }
        }
    }
}
