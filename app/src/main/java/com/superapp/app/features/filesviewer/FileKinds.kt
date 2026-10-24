package com.superapp.app.features.filesviewer

import com.superapp.app.features.files.model.FileItem

/** Which viewer draws a file. */
enum class ViewerKind(val label: String) {
    JSON("JSON"), XML("XML"), TOML("TOML"), SHELL("Shell script"),
    ENV("Env file"), CSV("CSV"), TSV("TSV"), TEXT("Text")
}

/**
 * Decides which viewer a file gets, by its NAME (not only the extension, because
 * ".env" and ".bashrc" have no real extension).
 *
 * kindOf() returns null for files this feature does not claim, so Files keeps opening
 * them with an external app exactly like before.
 *
 * HOW TO ADD A TYPE: add the extension to a set below (or a new set + one line in kindOf()).
 */
object FileKinds {
    private val json = setOf("json", "jsonc", "geojson", "webmanifest")
    private val xml = setOf("xml", "svg", "plist", "xsd", "xsl", "xslt", "kml", "gpx", "rss", "atom", "resx")
    private val shell = setOf("sh", "bash", "zsh", "ksh")
    private val shellNames = setOf(".bashrc", ".zshrc", ".profile", ".bash_profile", ".bash_aliases", ".zprofile")
    private val text = setOf("txt", "log", "md", "markdown", "ini", "conf", "cfg", "yml", "yaml", "properties")
    private val textNames = setOf(".gitignore", ".gitattributes", ".editorconfig")

    fun kindOf(name: String): ViewerKind? {
        val base = name.lowercase()
        if (base == ".env" || base.startsWith(".env.") || base.endsWith(".env")) return ViewerKind.ENV
        if (base in shellNames) return ViewerKind.SHELL
        if (base in textNames) return ViewerKind.TEXT
        return when (base.substringAfterLast('.', "")) {
            in json -> ViewerKind.JSON
            in xml -> ViewerKind.XML
            "toml" -> ViewerKind.TOML
            in shell -> ViewerKind.SHELL
            "csv" -> ViewerKind.CSV
            "tsv" -> ViewerKind.TSV
            in text -> ViewerKind.TEXT
            else -> null
        }
    }

    /** Used by Files: a tap opens the Files Viewer only for these. */
    fun isViewable(item: FileItem): Boolean = !item.isDirectory && kindOf(item.name) != null
}
