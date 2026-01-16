package no.doffin.documents.mcp.client

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import no.doffin.documents.mcp.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class DocumentsClient(
    @Value("\${documents.mock.enabled:true}") private val mockEnabled: Boolean,
    @Value("\${documents.output.dir:./generated-documents}") private val outputDir: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(DocumentsClient::class.java)

    // In-memory storage for generated documents and templates
    private val generatedDocuments = ConcurrentHashMap<String, GeneratedDocument>()
    private val templates = ConcurrentHashMap<String, DocumentTemplate>()

    // Brand guidelines (mock)
    private val brandGuidelines = mapOf(
        "companyName" to "Webstep",
        "primaryColor" to "#003366",
        "secondaryColor" to "#0066CC",
        "fontFamily" to "Arial, Helvetica, sans-serif",
        "logoRequired" to true,
        "forbiddenTerms" to listOf("guarantee", "promise", "100%"),
        "requiredSections" to listOf("Company Profile", "Contact Information")
    )

    @PostConstruct
    fun init() {
        if (mockEnabled) {
            logger.info("Documents client running in MOCK mode")
            initializeMockData()
        } else {
            logger.info("Documents client running in LIVE mode")
        }
    }

    private fun initializeMockData() {
        // Initialize mock templates
        val mockTemplates = listOf(
            DocumentTemplate(
                id = "template-pdf-standard",
                name = "Standard Bid PDF",
                description = "Standard PDF template for public sector bids",
                format = DocumentFormat.PDF,
                sections = listOf(
                    TemplateSection("cover", "Cover Page", true, 1, null),
                    TemplateSection("toc", "Table of Contents", true, 2, null),
                    TemplateSection("executive-summary", "Executive Summary", true, 3, null),
                    TemplateSection("company-profile", "Company Profile", true, 4, null),
                    TemplateSection("solution", "Proposed Solution", true, 5, null),
                    TemplateSection("team", "Team Composition", true, 6, null),
                    TemplateSection("methodology", "Methodology", true, 7, null),
                    TemplateSection("pricing", "Pricing", false, 8, null),
                    TemplateSection("references", "References", false, 9, null),
                    TemplateSection("appendices", "Appendices", false, 10, null)
                ),
                styling = DocumentStyling(
                    fontFamily = "Arial",
                    fontSize = 11,
                    headerColor = "#003366",
                    logoPosition = "top-right",
                    margins = DocumentMargins(25, 25, 25, 25)
                )
            ),
            DocumentTemplate(
                id = "template-word-standard",
                name = "Standard Bid Word Document",
                description = "Standard Word template for editable bid documents",
                format = DocumentFormat.WORD,
                sections = listOf(
                    TemplateSection("cover", "Cover Page", true, 1, null),
                    TemplateSection("executive-summary", "Executive Summary", true, 2, null),
                    TemplateSection("requirements", "Understanding of Requirements", true, 3, null),
                    TemplateSection("solution", "Proposed Solution", true, 4, null),
                    TemplateSection("team", "Team and CVs", true, 5, null),
                    TemplateSection("delivery", "Delivery Approach", true, 6, null),
                    TemplateSection("quality", "Quality Assurance", true, 7, null),
                    TemplateSection("pricing", "Commercial Terms", false, 8, null)
                ),
                styling = DocumentStyling(
                    fontFamily = "Calibri",
                    fontSize = 11,
                    headerColor = "#003366",
                    logoPosition = "header",
                    margins = DocumentMargins(20, 20, 25, 25)
                )
            )
        )

        mockTemplates.forEach { templates[it.id] = it }
        logger.info("Initialized ${templates.size} document templates")
    }

    /**
     * Generate a PDF document from bid content.
     */
    fun generatePdf(
        bidId: String,
        title: String,
        sections: List<Map<String, Any>>,
        metadata: Map<String, Any>?,
        templateId: String?
    ): Map<String, Any> {
        val docId = "doc-${UUID.randomUUID().toString().substring(0, 8)}"
        val now = LocalDateTime.now()
        val fileName = "${sanitizeFileName(title)}_${bidId}.pdf"

        // Convert sections
        val docSections = sections.mapIndexed { index, section ->
            DocumentSection(
                name = section["name"] as? String ?: "Section ${index + 1}",
                content = section["content"] as? String ?: "",
                order = section["order"] as? Int ?: (index + 1),
                pageBreakBefore = section["pageBreakBefore"] as? Boolean ?: false
            )
        }

        // Calculate word count
        val wordCount = docSections.sumOf { it.content.split("\\s+".toRegex()).size }

        val document = GeneratedDocument(
            id = docId,
            bidId = bidId,
            format = DocumentFormat.PDF,
            fileName = fileName,
            filePath = if (mockEnabled) null else "$outputDir/$fileName",
            fileSize = estimateFileSize(wordCount, DocumentFormat.PDF),
            generatedAt = now,
            generatedBy = metadata?.get("author") as? String ?: "system",
            sections = docSections,
            metadata = DocumentMetadata(
                title = title,
                author = metadata?.get("author") as? String ?: "Webstep",
                company = metadata?.get("company") as? String ?: "Webstep AS",
                version = metadata?.get("version") as? String ?: "1.0",
                language = metadata?.get("language") as? String ?: "no",
                pageCount = estimatePageCount(wordCount),
                wordCount = wordCount
            )
        )

        generatedDocuments[docId] = document
        logger.info("Generated PDF document: $docId - $fileName")

        return mapOf(
            "documentId" to document.id,
            "bidId" to document.bidId,
            "format" to document.format.name,
            "fileName" to document.fileName,
            "fileSize" to document.fileSize,
            "fileSizeFormatted" to formatFileSize(document.fileSize),
            "generatedAt" to document.generatedAt.toString(),
            "metadata" to mapOf(
                "title" to document.metadata.title,
                "author" to document.metadata.author,
                "company" to document.metadata.company,
                "version" to document.metadata.version,
                "pageCount" to document.metadata.pageCount,
                "wordCount" to document.metadata.wordCount
            ),
            "sections" to docSections.map { mapOf("name" to it.name, "order" to it.order) },
            "status" to "generated",
            "message" to if (mockEnabled) "Document generated (mock mode - no file created)" else "Document generated successfully"
        )
    }

    /**
     * Generate a Word document from bid content.
     */
    fun generateWord(
        bidId: String,
        title: String,
        sections: List<Map<String, Any>>,
        metadata: Map<String, Any>?,
        templateId: String?
    ): Map<String, Any> {
        val docId = "doc-${UUID.randomUUID().toString().substring(0, 8)}"
        val now = LocalDateTime.now()
        val fileName = "${sanitizeFileName(title)}_${bidId}.docx"

        val docSections = sections.mapIndexed { index, section ->
            DocumentSection(
                name = section["name"] as? String ?: "Section ${index + 1}",
                content = section["content"] as? String ?: "",
                order = section["order"] as? Int ?: (index + 1),
                pageBreakBefore = section["pageBreakBefore"] as? Boolean ?: false
            )
        }

        val wordCount = docSections.sumOf { it.content.split("\\s+".toRegex()).size }

        val document = GeneratedDocument(
            id = docId,
            bidId = bidId,
            format = DocumentFormat.WORD,
            fileName = fileName,
            filePath = if (mockEnabled) null else "$outputDir/$fileName",
            fileSize = estimateFileSize(wordCount, DocumentFormat.WORD),
            generatedAt = now,
            generatedBy = metadata?.get("author") as? String ?: "system",
            sections = docSections,
            metadata = DocumentMetadata(
                title = title,
                author = metadata?.get("author") as? String ?: "Webstep",
                company = metadata?.get("company") as? String ?: "Webstep AS",
                version = metadata?.get("version") as? String ?: "1.0",
                language = metadata?.get("language") as? String ?: "no",
                pageCount = estimatePageCount(wordCount),
                wordCount = wordCount
            )
        )

        generatedDocuments[docId] = document
        logger.info("Generated Word document: $docId - $fileName")

        return mapOf(
            "documentId" to document.id,
            "bidId" to document.bidId,
            "format" to document.format.name,
            "fileName" to document.fileName,
            "fileSize" to document.fileSize,
            "fileSizeFormatted" to formatFileSize(document.fileSize),
            "generatedAt" to document.generatedAt.toString(),
            "metadata" to mapOf(
                "title" to document.metadata.title,
                "author" to document.metadata.author,
                "company" to document.metadata.company,
                "version" to document.metadata.version,
                "pageCount" to document.metadata.pageCount,
                "wordCount" to document.metadata.wordCount
            ),
            "sections" to docSections.map { mapOf("name" to it.name, "order" to it.order) },
            "status" to "generated",
            "message" to if (mockEnabled) "Document generated (mock mode - no file created)" else "Document generated successfully"
        )
    }

    /**
     * Check compliance against tender requirements.
     */
    fun checkCompliance(
        bidId: String,
        requirements: List<String>,
        bidSections: List<Map<String, Any>>
    ): Map<String, Any> {
        val now = LocalDateTime.now()
        val checks = mutableListOf<ComplianceCheck>()
        val missingRequirements = mutableListOf<String>()

        // Convert bid sections to searchable content
        val sectionContents = bidSections.associate {
            (it["name"] as? String ?: "") to (it["content"] as? String ?: "")
        }
        val allContent = sectionContents.values.joinToString(" ").lowercase()

        // Check each requirement
        for (requirement in requirements) {
            val reqLower = requirement.lowercase()
            val found = allContent.contains(reqLower) ||
                    sectionContents.keys.any { it.lowercase().contains(reqLower) }

            val matchingSection = sectionContents.entries.find {
                it.value.lowercase().contains(reqLower) || it.key.lowercase().contains(reqLower)
            }?.key

            if (found) {
                checks.add(ComplianceCheck(
                    requirement = requirement,
                    category = categorizeRequirement(requirement),
                    status = ComplianceStatus.COMPLIANT,
                    details = "Requirement addressed in bid content",
                    section = matchingSection
                ))
            } else {
                checks.add(ComplianceCheck(
                    requirement = requirement,
                    category = categorizeRequirement(requirement),
                    status = ComplianceStatus.NON_COMPLIANT,
                    details = "Requirement not found in bid content",
                    section = null
                ))
                missingRequirements.add(requirement)
            }
        }

        // Add standard compliance checks
        val standardChecks = performStandardComplianceChecks(sectionContents)
        checks.addAll(standardChecks)

        // Calculate overall status
        val compliantCount = checks.count { it.status == ComplianceStatus.COMPLIANT }
        val score = if (checks.isNotEmpty()) compliantCount.toDouble() / checks.size else 0.0

        val overallStatus = when {
            score >= 0.9 -> ComplianceStatus.COMPLIANT
            score >= 0.6 -> ComplianceStatus.PARTIAL
            else -> ComplianceStatus.NON_COMPLIANT
        }

        // Generate recommendations
        val recommendations = generateComplianceRecommendations(checks, missingRequirements)

        return mapOf(
            "bidId" to bidId,
            "checkedAt" to now.toString(),
            "overallStatus" to overallStatus.name,
            "score" to score,
            "scorePercentage" to "${(score * 100).toInt()}%",
            "totalChecks" to checks.size,
            "compliantChecks" to compliantCount,
            "checks" to checks.map { mapOf(
                "requirement" to it.requirement,
                "category" to it.category,
                "status" to it.status.name,
                "details" to it.details,
                "section" to it.section
            )},
            "missingRequirements" to missingRequirements,
            "recommendations" to recommendations
        )
    }

    /**
     * Validate brand consistency.
     */
    fun validateBrand(
        bidId: String,
        bidContent: Map<String, Any>
    ): Map<String, Any> {
        val now = LocalDateTime.now()
        val checks = mutableListOf<BrandCheck>()
        val issues = mutableListOf<BrandIssue>()

        val title = bidContent["title"] as? String ?: ""
        val sections = (bidContent["sections"] as? List<*>)?.mapNotNull {
            it as? Map<*, *>
        } ?: emptyList()
        val allContent = sections.mapNotNull { it["content"] as? String }.joinToString(" ")

        // Check company name usage
        val companyName = brandGuidelines["companyName"] as String
        val companyMentions = allContent.lowercase().split(companyName.lowercase()).size - 1
        if (companyMentions > 0) {
            checks.add(BrandCheck("Company Name", BrandStatus.VALID, "Company name '$companyName' mentioned $companyMentions times"))
        } else {
            checks.add(BrandCheck("Company Name", BrandStatus.WARNINGS, "Company name not found in content"))
            issues.add(BrandIssue("missing_branding", "Company name not mentioned in bid", null, "Add company name in introduction and relevant sections"))
        }

        // Check for forbidden terms
        val forbiddenTerms = brandGuidelines["forbiddenTerms"] as List<*>
        for (term in forbiddenTerms) {
            if (allContent.lowercase().contains(term.toString().lowercase())) {
                checks.add(BrandCheck("Forbidden Term: $term", BrandStatus.INVALID, "Found forbidden term '$term' in content"))
                issues.add(BrandIssue("forbidden_term", "Forbidden term '$term' used", null, "Replace '$term' with more appropriate language"))
            }
        }
        if (forbiddenTerms.none { allContent.lowercase().contains(it.toString().lowercase()) }) {
            checks.add(BrandCheck("Forbidden Terms", BrandStatus.VALID, "No forbidden terms found"))
        }

        // Check required sections
        val requiredSections = brandGuidelines["requiredSections"] as List<*>
        val sectionNames = sections.mapNotNull { it["name"] as? String }.map { it.lowercase() }
        for (required in requiredSections) {
            val reqLower = required.toString().lowercase()
            if (sectionNames.any { it.contains(reqLower) || reqLower.contains(it) }) {
                checks.add(BrandCheck("Required Section: $required", BrandStatus.VALID, "Section present"))
            } else {
                checks.add(BrandCheck("Required Section: $required", BrandStatus.WARNINGS, "Section missing or not clearly labeled"))
                issues.add(BrandIssue("missing_section", "Required section '$required' not found", null, "Add a section for '$required'"))
            }
        }

        // Check title format
        if (title.isNotBlank() && title.length >= 10) {
            checks.add(BrandCheck("Title Format", BrandStatus.VALID, "Title is properly formatted"))
        } else {
            checks.add(BrandCheck("Title Format", BrandStatus.WARNINGS, "Title may be too short or missing"))
            issues.add(BrandIssue("title_format", "Title appears incomplete", "Title", "Ensure title clearly describes the bid"))
        }

        // Calculate overall status
        val invalidCount = checks.count { it.status == BrandStatus.INVALID }
        val warningCount = checks.count { it.status == BrandStatus.WARNINGS }
        val validCount = checks.count { it.status == BrandStatus.VALID }

        val overallStatus = when {
            invalidCount > 0 -> BrandStatus.INVALID
            warningCount > 0 -> BrandStatus.WARNINGS
            else -> BrandStatus.VALID
        }

        val score = if (checks.isNotEmpty()) {
            (validCount * 1.0 + warningCount * 0.5) / checks.size
        } else 1.0

        return mapOf(
            "bidId" to bidId,
            "checkedAt" to now.toString(),
            "overallStatus" to overallStatus.name,
            "score" to score,
            "scorePercentage" to "${(score * 100).toInt()}%",
            "checks" to checks.map { mapOf(
                "aspect" to it.aspect,
                "status" to it.status.name,
                "details" to it.details
            )},
            "issues" to issues.map { mapOf(
                "type" to it.type,
                "description" to it.description,
                "location" to it.location,
                "suggestion" to it.suggestion
            )},
            "summary" to mapOf(
                "valid" to validCount,
                "warnings" to warningCount,
                "invalid" to invalidCount
            )
        )
    }

    /**
     * Apply a template to bid content.
     */
    fun applyTemplate(
        templateId: String,
        bidContent: Map<String, Any>
    ): Map<String, Any> {
        val template = templates[templateId]
            ?: throw NoSuchElementException("Template not found: $templateId")

        val bidSections = (bidContent["sections"] as? List<*>)?.mapNotNull {
            it as? Map<*, *>
        } ?: emptyList()

        val appliedSections = mutableListOf<Map<String, Any>>()

        // Map bid sections to template sections
        for (templateSection in template.sections.sortedBy { it.order }) {
            val matchingBidSection = bidSections.find { bidSection ->
                val bidSectionName = (bidSection["name"] as? String)?.lowercase() ?: ""
                bidSectionName.contains(templateSection.name.lowercase()) ||
                        templateSection.name.lowercase().contains(bidSectionName) ||
                        templateSection.id.lowercase() == bidSectionName
            }

            appliedSections.add(mapOf(
                "templateSection" to templateSection.name,
                "templateSectionId" to templateSection.id,
                "order" to templateSection.order,
                "required" to templateSection.required,
                "content" to (matchingBidSection?.get("content") as? String ?: templateSection.defaultContent ?: ""),
                "status" to if (matchingBidSection != null) "mapped" else if (templateSection.required) "missing" else "optional"
            ))
        }

        val mappedCount = appliedSections.count { it["status"] == "mapped" }
        val missingRequired = appliedSections.filter { it["status"] == "missing" && it["required"] == true }

        return mapOf(
            "templateId" to template.id,
            "templateName" to template.name,
            "format" to template.format.name,
            "sections" to appliedSections,
            "styling" to mapOf(
                "fontFamily" to template.styling.fontFamily,
                "fontSize" to template.styling.fontSize,
                "headerColor" to template.styling.headerColor,
                "logoPosition" to template.styling.logoPosition
            ),
            "summary" to mapOf(
                "totalSections" to template.sections.size,
                "mappedSections" to mappedCount,
                "missingSections" to appliedSections.count { it["status"] == "missing" },
                "optionalSections" to appliedSections.count { it["status"] == "optional" }
            ),
            "warnings" to if (missingRequired.isNotEmpty()) {
                missingRequired.map { "Required section '${it["templateSection"]}' has no content" }
            } else emptyList<String>()
        )
    }

    /**
     * List available templates.
     */
    fun listTemplates(format: String?): Map<String, Any> {
        var templateList = templates.values.toList()

        if (!format.isNullOrBlank()) {
            val docFormat = try {
                DocumentFormat.valueOf(format.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid format: $format. Valid values: ${DocumentFormat.entries.joinToString()}")
            }
            templateList = templateList.filter { it.format == docFormat }
        }

        return mapOf(
            "templates" to templateList.map { mapOf(
                "id" to it.id,
                "name" to it.name,
                "description" to it.description,
                "format" to it.format.name,
                "sectionCount" to it.sections.size
            )},
            "total" to templateList.size
        )
    }

    /**
     * Get a generated document by ID.
     */
    fun getDocument(documentId: String): Map<String, Any?> {
        val doc = generatedDocuments[documentId]
            ?: throw NoSuchElementException("Document not found: $documentId")

        return mapOf(
            "documentId" to doc.id,
            "bidId" to doc.bidId,
            "format" to doc.format.name,
            "fileName" to doc.fileName,
            "filePath" to doc.filePath,
            "fileSize" to doc.fileSize,
            "fileSizeFormatted" to formatFileSize(doc.fileSize),
            "generatedAt" to doc.generatedAt.toString(),
            "generatedBy" to doc.generatedBy,
            "metadata" to mapOf(
                "title" to doc.metadata.title,
                "author" to doc.metadata.author,
                "company" to doc.metadata.company,
                "version" to doc.metadata.version,
                "pageCount" to doc.metadata.pageCount,
                "wordCount" to doc.metadata.wordCount
            ),
            "sections" to doc.sections.map { mapOf(
                "name" to it.name,
                "order" to it.order,
                "contentLength" to it.content.length
            )}
        )
    }

    // Helper methods

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9\\-_]"), "_").take(50)

    private fun estimateFileSize(wordCount: Int, format: DocumentFormat): Long {
        return when (format) {
            DocumentFormat.PDF -> (wordCount * 8 + 50000).toLong() // ~8 bytes per word + overhead
            DocumentFormat.WORD -> (wordCount * 10 + 30000).toLong() // ~10 bytes per word + overhead
            DocumentFormat.HTML -> (wordCount * 12 + 5000).toLong()
        }
    }

    private fun estimatePageCount(wordCount: Int): Int =
        maxOf(1, wordCount / 350) // ~350 words per page

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
            bytes >= 1_000 -> "${bytes / 1_000} KB"
            else -> "$bytes bytes"
        }
    }

    private fun categorizeRequirement(requirement: String): String {
        val reqLower = requirement.lowercase()
        return when {
            reqLower.contains("security") || reqLower.contains("gdpr") -> "Security"
            reqLower.contains("team") || reqLower.contains("resource") -> "Resources"
            reqLower.contains("price") || reqLower.contains("cost") -> "Commercial"
            reqLower.contains("quality") || reqLower.contains("test") -> "Quality"
            reqLower.contains("deliver") || reqLower.contains("timeline") -> "Delivery"
            else -> "General"
        }
    }

    private fun performStandardComplianceChecks(sections: Map<String, String>): List<ComplianceCheck> {
        val checks = mutableListOf<ComplianceCheck>()

        // Check for executive summary
        if (sections.keys.any { it.lowercase().contains("summary") || it.lowercase().contains("executive") }) {
            checks.add(ComplianceCheck("Executive Summary", "Structure", ComplianceStatus.COMPLIANT, "Executive summary section present", null))
        } else {
            checks.add(ComplianceCheck("Executive Summary", "Structure", ComplianceStatus.PARTIAL, "Executive summary section recommended", null))
        }

        // Check for contact information
        val allContent = sections.values.joinToString(" ").lowercase()
        if (allContent.contains("@") && (allContent.contains("telefon") || allContent.contains("phone") || allContent.contains(Regex("\\d{8}")))) {
            checks.add(ComplianceCheck("Contact Information", "Structure", ComplianceStatus.COMPLIANT, "Contact details found", null))
        } else {
            checks.add(ComplianceCheck("Contact Information", "Structure", ComplianceStatus.PARTIAL, "Contact information may be incomplete", null))
        }

        return checks
    }

    private fun generateComplianceRecommendations(checks: List<ComplianceCheck>, missing: List<String>): List<String> {
        val recommendations = mutableListOf<String>()

        if (missing.isNotEmpty()) {
            recommendations.add("Address missing requirements: ${missing.take(3).joinToString(", ")}${if (missing.size > 3) " and ${missing.size - 3} more" else ""}")
        }

        val partialChecks = checks.filter { it.status == ComplianceStatus.PARTIAL }
        if (partialChecks.isNotEmpty()) {
            recommendations.add("Strengthen sections for: ${partialChecks.take(2).joinToString(", ") { it.requirement }}")
        }

        val nonCompliant = checks.filter { it.status == ComplianceStatus.NON_COMPLIANT }
        if (nonCompliant.size > checks.size * 0.3) {
            recommendations.add("Review tender requirements carefully - significant gaps identified")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Bid appears well-aligned with requirements")
        }

        return recommendations
    }
}
