package com.superapp.app.features.editor.syntax

data class Language(
    val id: String,
    val displayName: String,
    val extensions: Set<String>,
    val lineComment: String? = null,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
    val stringDelimiters: List<String> = listOf("\"", "'"),
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val constants: Set<String> = emptySet(),
    val mode: LanguageMode = LanguageMode.C_LIKE
)

enum class LanguageMode { C_LIKE, HTML, CSS, PYTHON }

object LanguageRegistry {

    val html = Language(
        id = "html", displayName = "HTML",
        extensions = setOf("html", "htm", "xhtml", "xml", "svg", "vue"),
        mode = LanguageMode.HTML
    )

    val css = Language(
        id = "css", displayName = "CSS",
        extensions = setOf("css", "scss", "sass", "less"),
        mode = LanguageMode.CSS
    )

    val javascript = Language(
        id = "javascript", displayName = "JavaScript",
        extensions = setOf("js", "mjs", "cjs", "jsx"),
        lineComment = "//",
        blockCommentStart = "/*", blockCommentEnd = "*/",
        stringDelimiters = listOf("\"", "'", "`"),
        keywords = setOf(
            "abstract","arguments","await","async","break","case","catch","class",
            "const","continue","debugger","default","delete","do","else","enum",
            "export","extends","finally","for","function","get","if","implements",
            "import","in","instanceof","interface","let","new","of","package",
            "private","protected","public","return","set","static","super","switch",
            "this","throw","try","typeof","var","void","while","with","yield","null",
            "true","false","undefined","NaN","Infinity"
        ),
        types = setOf("Array","Boolean","Date","Error","Function","JSON","Map","Math","Number",
            "Object","Promise","RegExp","Set","String","Symbol","WeakMap","WeakSet","console")
    )

    val python = Language(
        id = "python", displayName = "Python",
        extensions = setOf("py", "pyw", "pyi"),
        lineComment = "#",
        blockCommentStart = "\"\"\"", blockCommentEnd = "\"\"\"",
        stringDelimiters = listOf("\"\"\"", "'''", "\"", "'"),
        mode = LanguageMode.PYTHON,
        keywords = setOf(
            "and","as","assert","async","await","break","class","continue","def",
            "del","elif","else","except","False","finally","for","from","global",
            "if","import","in","is","lambda","None","nonlocal","not","or","pass",
            "raise","return","True","try","while","with","yield","match","case",
            "self","cls"
        ),
        types = setOf("bool","bytes","dict","float","frozenset","int","list","object",
            "set","str","tuple","type","complex"),
        constants = setOf("__name__", "__file__", "__main__")
    )

    val c = Language(
        id = "c", displayName = "C",
        extensions = setOf("c", "h"),
        lineComment = "//",
        blockCommentStart = "/*", blockCommentEnd = "*/",
        keywords = setOf(
            "auto","break","case","char","const","continue","default","do","double",
            "else","enum","extern","float","for","goto","if","inline","int","long",
            "register","restrict","return","short","signed","sizeof","static",
            "struct","switch","typedef","union","unsigned","void","volatile","while",
            "_Bool","_Complex","_Imaginary"
        ),
        types = setOf("size_t","ssize_t","int8_t","int16_t","int32_t","int64_t",
            "uint8_t","uint16_t","uint32_t","uint64_t","FILE","time_t")
    )

    val cpp = Language(
        id = "cpp", displayName = "C++",
        extensions = setOf("cpp", "cc", "cxx", "hpp", "hh", "hxx", "c++", "h++"),
        lineComment = "//",
        blockCommentStart = "/*", blockCommentEnd = "*/",
        keywords = setOf(
            "alignas","alignof","and","asm","auto","bitand","bitor","bool","break",
            "case","catch","char","char8_t","char16_t","char32_t","class","compl",
            "concept","const","consteval","constexpr","constinit","const_cast",
            "continue","co_await","co_return","co_yield","decltype","default","delete",
            "do","double","dynamic_cast","else","enum","explicit","export","extern",
            "false","float","for","friend","goto","if","inline","int","long","mutable",
            "namespace","new","noexcept","not","nullptr","operator","or","private",
            "protected","public","register","reinterpret_cast","requires","return",
            "short","signed","sizeof","static","static_assert","static_cast","struct",
            "switch","template","this","thread_local","throw","true","try","typedef",
            "typeid","typename","union","unsigned","using","virtual","void","volatile",
            "wchar_t","while","xor"
        ),
        types = setOf("string","vector","map","set","unordered_map","unordered_set",
            "array","deque","list","queue","stack","pair","tuple","optional",
            "variant","unique_ptr","shared_ptr","weak_ptr","function","size_t")
    )

    val kotlin = Language(
        id = "kotlin", displayName = "Kotlin",
        extensions = setOf("kt", "kts"),
        lineComment = "//",
        blockCommentStart = "/*", blockCommentEnd = "*/",
        stringDelimiters = listOf("\"\"\"", "\"", "'"),
        keywords = setOf(
            "as","break","class","continue","do","else","false","for","fun","if",
            "in","interface","is","null","object","package","return","super","this",
            "throw","true","try","typealias","typeof","val","var","when","while","by",
            "catch","constructor","delegate","dynamic","field","file","finally","get",
            "import","init","param","property","receiver","set","setparam","where",
            "actual","abstract","annotation","companion","const","crossinline","data",
            "enum","expect","external","final","infix","inline","inner","internal",
            "lateinit","noinline","open","operator","out","override","private",
            "protected","public","reified","sealed","suspend","tailrec","vararg"
        ),
        types = setOf("Any","Boolean","Byte","Char","Double","Float","Int","Long",
            "Nothing","Short","String","Unit","Array","List","Map","Set","Pair",
            "Triple","Sequence","Collection","Iterable")
    )

    val all = listOf(html, css, javascript, python, c, cpp, kotlin)

    private val extMap: Map<String, Language> = all
        .flatMap { lang -> lang.extensions.map { it.lowercase() to lang } }
        .toMap()

    fun forFile(name: String): Language? {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return extMap[ext]
    }
}
