package no.doffin.documents.mcp.model

import java.time.LocalDateTime

/**
 * Represents a generated document.
 */
data class GeneratedDocument(
    val id: String,
    val bidId: String,
    val format: DocumentFormat,
    val fileName: String,
    val filePath: String?,
    val fileSize: Long,
    val generatedAt: LocalDateTime,
    val generatedBy: String,
    val sections: List<DocumentSection>,
    val metadata: DocumentMetadata
)

enum class DocumentFormat {
    PDF,
    WORD,
    HTML
}

data class DocumentSection(
    val name: String,
    val content: String,
    val order: Int,
    val pageBreakBefore: Boolean = false
)

data class DocumentMetadata(
    val title: String,
    val author: String,
    val company: String,
    val version: String,
    val language: String = "no",
    val pageCount: Int?,
    val wordCount: Int
)

/**
 * Result of compliance check.
 */
data class ComplianceCheckResult(
    val bidId: String,
    val checkedAt: LocalDateTime,
    val overallStatus: ComplianceStatus,
    val score: Double,
    val checks: List<ComplianceCheck>,
    val missingRequirements: List<String>,
    val recommendations: List<String>
)

enum class ComplianceStatus {
    COMPLIANT,
    PARTIAL,
    NON_COMPLIANT
}

data class ComplianceCheck(
    val requirement: String,
    val category: String,
    val status: ComplianceStatus,
    val details: String,
    val section: String?
)

/**
 * Result of brand validation.
 */
data class BrandValidationResult(
    val bidId: String,
    val checkedAt: LocalDateTime,
    val overallStatus: BrandStatus,
    val score: Double,
    val checks: List<BrandCheck>,
    val issues: List<BrandIssue>
)

enum class BrandStatus {
    VALID,
    WARNINGS,
    INVALID
}

data class BrandCheck(
    val aspect: String,
    val status: BrandStatus,
    val details: String
)

data class BrandIssue(
    val type: String,
    val description: String,
    val location: String?,
    val suggestion: String
)

/**
 * Document template for generation.
 */
data class DocumentTemplate(
    val id: String,
    val name: String,
    val description: String,
    val format: DocumentFormat,
    val sections: List<TemplateSection>,
    val styling: DocumentStyling
)

data class TemplateSection(
    val id: String,
    val name: String,
    val required: Boolean,
    val order: Int,
    val defaultContent: String?
)

data class DocumentStyling(
    val fontFamily: String,
    val fontSize: Int,
    val headerColor: String,
    val logoPosition: String?,
    val margins: DocumentMargins
)

data class DocumentMargins(
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int
)

/**
 * Bid content for document generation.
 */
data class BidContent(
    val bidId: String,
    val title: String,
    val sections: List<BidSection>,
    val metadata: BidMetadata
)

data class BidSection(
    val name: String,
    val content: String,
    val order: Int
)

data class BidMetadata(
    val opportunityId: String?,
    val opportunityTitle: String?,
    val deadline: String?,
    val company: String,
    val author: String,
    val version: String
)
