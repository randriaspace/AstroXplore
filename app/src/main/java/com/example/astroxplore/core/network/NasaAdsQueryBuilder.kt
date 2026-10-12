package com.example.astroxplore.core.network

import com.example.astroxplore.features.search.ui.SearchFilter

object NasaAdsQueryBuilder {

    enum class RefereedFilter {
        ALL, REFEREED_ONLY, PREPRINTS_ONLY
    }

    /**
     * Translates user preference tags into a unified Solr query
     */
    fun buildPreferenceQuery(
        tags: List<String>,
        refereedFilter: RefereedFilter = RefereedFilter.ALL
    ): String {
        if (tags.isEmpty()) {
            return "keyword:astro-ph AND property:eprint"
        }

        val catClauses = mutableListOf<String>()
        val topicClauses = mutableListOf<String>()

        for (tag in tags) {
            val cleanTag = if (tag.startsWith("#")) tag.substring(1) else tag

            if (cleanTag.startsWith("astro-ph")) {
                catClauses.add("keyword:\"$cleanTag\"")
            } else {
                topicClauses.add("(title:\"$cleanTag\" OR abs:\"$cleanTag\" OR keyword:\"$cleanTag\")")
            }
        }

        val baseQuery = when {
            catClauses.isNotEmpty() && topicClauses.isNotEmpty() -> {
                "((${catClauses.joinToString(" OR ")}) OR (${topicClauses.joinToString(" OR ")}))"
            }
            catClauses.isNotEmpty() -> "(${catClauses.joinToString(" OR ")})"
            topicClauses.isNotEmpty() -> "(${topicClauses.joinToString(" OR ")})"
            else -> "*:*"
        }

        return when (refereedFilter) {
            RefereedFilter.REFEREED_ONLY -> "$baseQuery AND property:refereed"
            RefereedFilter.PREPRINTS_ONLY -> "$baseQuery AND property:eprint"
            RefereedFilter.ALL -> baseQuery
        }
    }

    /**
     * Processes raw search input into an optimal ADS query
     */
    fun buildQuickSearchQuery(rawInput: String): String {
        val input = rawInput.trim()
        if (input.isEmpty()) return "*:*"

        // 1. Bibcode Pattern: 2026ApJ...900...12V
        val bibcodeRegex = Regex("^\\d{4}[A-Za-z0-9.&]{15}$")
        if (bibcodeRegex.matches(input)) {
            return "bibcode:\"$input\""
        }

        // 2. arXiv ID Pattern: 2308.12345 or arXiv:2308.12345
        val arxivRegex = Regex("^(arXiv:)?\\d{4}\\.\\d{4,5}(v\\d+)?$")
        if (arxivRegex.matches(input)) {
            val arxivId = if (input.startsWith("arXiv:")) input else "arXiv:$input"
            return "identifier:\"$arxivId\""
        }

        // 3. Author Pattern: "Ghez, A" or "author:Hawking"
        if (input.contains(",") || input.lowercase().startsWith("author:")) {
            val author = if (input.lowercase().startsWith("author:")) {
                input.substring(7).trim()
            } else {
                input
            }
            return "author:\"$author\""
        }

        // 4. Raw Solr Query or Pre-constructed Clause: Contains operators or field syntax like title:, abs:, property:, AND, OR
        if (input.contains(":") || input.contains(" AND ") || input.contains(" OR ") || input.startsWith("(") || input.contains("NOT ")) {
            return input
        }

        // 5. Fallback: Title and Abstract for free-text search keywords
        return "(title:\"$input\" OR abs:\"$input\" OR author:\"$input\")"
    }

    /**
     * Builds a structured ADS query using specific filters
     */
    fun buildAdvancedQuery(
        query: String,
        filter: SearchFilter
    ): String {
        val clauses = mutableListOf<String>()

        if (query.isNotBlank()) {
            clauses.add(buildQuickSearchQuery(query))
        }

        filter.yearRange?.let {
            clauses.add("year:[${it.first} TO ${it.last}]")
        }

        if (filter.refereedOnly) {
            clauses.add("property:refereed")
        }

        if (filter.isOpenAccess) {
            clauses.add("property:openaccess")
        }

        if (filter.hasData) {
            clauses.add("property:data")
        }

        if (filter.hasSoftware) {
            clauses.add("property:software")
        }

        filter.fullText?.takeIf { it.isNotBlank() }?.let {
            clauses.add("body:\"$it\"")
        }

        filter.bibstem?.takeIf { it.isNotBlank() }?.let {
            clauses.add("bibstem:\"$it\"")
        }

        filter.author?.takeIf { it.isNotBlank() }?.let {
            clauses.add("author:\"$it\"")
        }

        filter.firstAuthor?.takeIf { it.isNotBlank() }?.let {
            clauses.add("author:\"^$it\"")
        }

        filter.orcid?.takeIf { it.isNotBlank() }?.let {
            clauses.add("orcid:\"$it\"")
        }

        filter.abstractOnly?.takeIf { it.isNotBlank() }?.let {
            clauses.add("abstract:\"$it\"")
        }

        filter.titleOnly?.takeIf { it.isNotBlank() }?.let {
            clauses.add("title:\"$it\"")
        }

        filter.affiliation?.takeIf { it.isNotBlank() }?.let {
            clauses.add("aff:\"$it\"")
        }

        filter.objectName?.takeIf { it.isNotBlank() }?.let {
            clauses.add("object:\"$it\"")
        }

        filter.arxivId?.takeIf { it.isNotBlank() }?.let {
            clauses.add("arXiv:\"$it\"")
        }

        filter.doi?.takeIf { it.isNotBlank() }?.let {
            clauses.add("doi:\"$it\"")
        }

        filter.volume?.takeIf { it.isNotBlank() }?.let {
            clauses.add("volume:\"$it\"")
        }

        filter.page?.takeIf { it.isNotBlank() }?.let {
            clauses.add("page:\"$it\"")
        }

        filter.bibcodePrefix?.takeIf { it.isNotBlank() }?.let {
            clauses.add("bibcode:$it*")
        }

        filter.citationCountMin?.let {
            clauses.add("citation_count:[$it TO *]")
        }

        // Pro Filters
        filter.arxivClass?.takeIf { it.isNotBlank() }?.let {
            clauses.add("arxiv_class:\"$it\"")
        }

        filter.authorCountRange?.let {
            clauses.add("author_count:[${it.first} TO ${it.last}]")
        }

        filter.bibGroup?.takeIf { it.isNotBlank() }?.let {
            clauses.add("bibgroup:\"$it\"")
        }

        filter.database?.takeIf { it.isNotBlank() }?.let {
            clauses.add("database:\"$it\"")
        }

        filter.docType?.takeIf { it.isNotBlank() }?.let {
            clauses.add("doctype:\"$it\"")
        }

        return if (clauses.isEmpty()) "*:*" else clauses.joinToString(" AND ")
    }
}
