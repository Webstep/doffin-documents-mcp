package no.doffin.documents.mcp.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DocumentsClientTest {

    private lateinit var client: DocumentsClient
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
        objectMapper.findAndRegisterModules() // For LocalDateTime support
        client = DocumentsClient(
            mockEnabled = true,
            outputDir = "./test-documents",
            objectMapper = objectMapper
        )
        client.init()
    }

    // PDF Generation Tests

    @Test
    fun `generatePdf creates PDF document`() {
        val result = client.generatePdf(
            bidId = "bid-001",
            title = "Test Bid Document",
            sections = listOf(
                mapOf("name" to "Executive Summary", "content" to "This is the executive summary.", "order" to 1),
                mapOf("name" to "Solution", "content" to "Our proposed solution.", "order" to 2)
            ),
            metadata = mapOf("author" to "Test Author", "company" to "Test Company"),
            templateId = null
        )

        assertThat(result["documentId"] as String).startsWith("doc-")
        assertThat(result["bidId"]).isEqualTo("bid-001")
        assertThat(result["format"]).isEqualTo("PDF")
        assertThat(result["fileName"] as String).endsWith(".pdf")
        assertThat(result["fileSize"] as Long).isGreaterThan(0)
        assertThat(result["status"]).isEqualTo("generated")
    }

    @Test
    fun `generatePdf calculates metadata correctly`() {
        val result = client.generatePdf(
            bidId = "bid-002",
            title = "Large Document",
            sections = listOf(
                mapOf("name" to "Section 1", "content" to "Word ".repeat(500), "order" to 1)
            ),
            metadata = null,
            templateId = null
        )

        val metadata = result["metadata"] as Map<*, *>
        assertThat(metadata["wordCount"] as Int).isGreaterThanOrEqualTo(500)
        assertThat(metadata["pageCount"] as Int).isGreaterThan(0)
    }

    // Word Generation Tests

    @Test
    fun `generateWord creates Word document`() {
        val result = client.generateWord(
            bidId = "bid-003",
            title = "Test Word Document",
            sections = listOf(
                mapOf("name" to "Introduction", "content" to "Introduction text.", "order" to 1)
            ),
            metadata = null,
            templateId = null
        )

        assertThat(result["format"]).isEqualTo("WORD")
        assertThat(result["fileName"] as String).endsWith(".docx")
    }

    @Test
    fun `generateWord handles multiple sections`() {
        val result = client.generateWord(
            bidId = "bid-004",
            title = "Multi-Section Document",
            sections = listOf(
                mapOf("name" to "Section 1", "content" to "Content 1", "order" to 1),
                mapOf("name" to "Section 2", "content" to "Content 2", "order" to 2),
                mapOf("name" to "Section 3", "content" to "Content 3", "order" to 3)
            ),
            metadata = null,
            templateId = null
        )

        val sections = result["sections"] as List<*>
        assertThat(sections.size).isEqualTo(3)
    }

    // Compliance Check Tests

    @Test
    fun `checkCompliance returns compliance result`() {
        val result = client.checkCompliance(
            bidId = "bid-005",
            requirements = listOf("security", "team experience"),
            bidSections = listOf(
                mapOf("name" to "Security", "content" to "We implement ISO 27001 security standards."),
                mapOf("name" to "Team", "content" to "Our team has extensive experience.")
            )
        )

        assertThat(result["bidId"]).isEqualTo("bid-005")
        assertThat(result["overallStatus"]).isNotNull
        assertThat(result["score"] as Double).isBetween(0.0, 1.0)
        assertThat(result["checks"]).isNotNull
    }

    @Test
    fun `checkCompliance identifies compliant requirements`() {
        val result = client.checkCompliance(
            bidId = "bid-006",
            requirements = listOf("security"),
            bidSections = listOf(
                mapOf("name" to "Security Section", "content" to "We have security measures in place.")
            )
        )

        val checks = result["checks"] as List<*>
        val securityCheck = checks.find { (it as Map<*, *>)["requirement"] == "security" } as Map<*, *>
        assertThat(securityCheck["status"]).isEqualTo("COMPLIANT")
    }

    @Test
    fun `checkCompliance identifies missing requirements`() {
        val result = client.checkCompliance(
            bidId = "bid-007",
            requirements = listOf("blockchain", "quantum computing"),
            bidSections = listOf(
                mapOf("name" to "Technology", "content" to "We use cloud computing.")
            )
        )

        val missingRequirements = result["missingRequirements"] as List<*>
        assertThat(missingRequirements).contains("blockchain", "quantum computing")
    }

    @Test
    fun `checkCompliance generates recommendations`() {
        val result = client.checkCompliance(
            bidId = "bid-008",
            requirements = listOf("missing requirement 1", "missing requirement 2"),
            bidSections = listOf(
                mapOf("name" to "Section", "content" to "Content")
            )
        )

        val recommendations = result["recommendations"] as List<*>
        assertThat(recommendations).isNotEmpty
    }

    // Brand Validation Tests

    @Test
    fun `validateBrand returns validation result`() {
        val result = client.validateBrand(
            bidId = "bid-009",
            bidContent = mapOf(
                "title" to "Webstep Bid for Services",
                "sections" to listOf(
                    mapOf("name" to "Company Profile", "content" to "Webstep is a leading IT consultancy.")
                )
            )
        )

        assertThat(result["bidId"]).isEqualTo("bid-009")
        assertThat(result["overallStatus"]).isNotNull
        assertThat(result["score"] as Double).isBetween(0.0, 1.0)
        assertThat(result["checks"]).isNotNull
    }

    @Test
    fun `validateBrand detects company name presence`() {
        val result = client.validateBrand(
            bidId = "bid-010",
            bidContent = mapOf(
                "title" to "Test Bid",
                "sections" to listOf(
                    mapOf("name" to "About", "content" to "Webstep delivers excellent IT services.")
                )
            )
        )

        val checks = result["checks"] as List<*>
        val companyCheck = checks.find { (it as Map<*, *>)["aspect"] == "Company Name" } as Map<*, *>
        assertThat(companyCheck["status"]).isEqualTo("VALID")
    }

    @Test
    fun `validateBrand detects forbidden terms`() {
        val result = client.validateBrand(
            bidId = "bid-011",
            bidContent = mapOf(
                "title" to "Test Bid",
                "sections" to listOf(
                    mapOf("name" to "Promise", "content" to "We guarantee 100% satisfaction.")
                )
            )
        )

        val issues = result["issues"] as List<*>
        assertThat(issues.any { (it as Map<*, *>)["type"] == "forbidden_term" }).isTrue()
    }

    @Test
    fun `validateBrand checks required sections`() {
        val result = client.validateBrand(
            bidId = "bid-012",
            bidContent = mapOf(
                "title" to "Test Bid",
                "sections" to listOf(
                    mapOf("name" to "Company Profile", "content" to "About us."),
                    mapOf("name" to "Contact Information", "content" to "Email: test@test.com")
                )
            )
        )

        val checks = result["checks"] as List<*>
        val requiredSectionChecks = checks.filter {
            (it as Map<*, *>)["aspect"].toString().startsWith("Required Section")
        }
        assertThat(requiredSectionChecks.all { (it as Map<*, *>)["status"] == "VALID" }).isTrue()
    }

    // Template Tests

    @Test
    fun `listTemplates returns all templates`() {
        val result = client.listTemplates(null)

        val templates = result["templates"] as List<*>
        assertThat(templates.size).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `listTemplates filters by format`() {
        val result = client.listTemplates("PDF")

        val templates = result["templates"] as List<*>
        templates.forEach { template ->
            val t = template as Map<*, *>
            assertThat(t["format"]).isEqualTo("PDF")
        }
    }

    @Test
    fun `listTemplates throws for invalid format`() {
        val exception = assertThrows<IllegalArgumentException> {
            client.listTemplates("INVALID")
        }
        assertThat(exception.message).contains("Invalid format")
    }

    @Test
    fun `applyTemplate maps sections correctly`() {
        val result = client.applyTemplate(
            templateId = "template-pdf-standard",
            bidContent = mapOf(
                "sections" to listOf(
                    mapOf("name" to "Executive Summary", "content" to "Summary content."),
                    mapOf("name" to "Company Profile", "content" to "Company description.")
                )
            )
        )

        assertThat(result["templateId"]).isEqualTo("template-pdf-standard")
        val sections = result["sections"] as List<*>
        assertThat(sections).isNotEmpty

        val mappedSections = sections.filter { (it as Map<*, *>)["status"] == "mapped" }
        assertThat(mappedSections.size).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `applyTemplate identifies missing required sections`() {
        val result = client.applyTemplate(
            templateId = "template-pdf-standard",
            bidContent = mapOf(
                "sections" to listOf(
                    mapOf("name" to "Random Section", "content" to "Some content.")
                )
            )
        )

        val summary = result["summary"] as Map<*, *>
        assertThat(summary["missingSections"] as Int).isGreaterThan(0)
    }

    @Test
    fun `applyTemplate throws for non-existent template`() {
        val exception = assertThrows<NoSuchElementException> {
            client.applyTemplate("non-existent", mapOf("sections" to emptyList<Any>()))
        }
        assertThat(exception.message).contains("Template not found")
    }

    // Document Retrieval Tests

    @Test
    fun `getDocument returns generated document`() {
        // First generate a document
        val generated = client.generatePdf(
            bidId = "bid-get-test",
            title = "Test Document",
            sections = listOf(mapOf("name" to "Section", "content" to "Content", "order" to 1)),
            metadata = null,
            templateId = null
        )
        val documentId = generated["documentId"] as String

        // Then retrieve it
        val result = client.getDocument(documentId)

        assertThat(result["documentId"]).isEqualTo(documentId)
        assertThat(result["bidId"]).isEqualTo("bid-get-test")
        assertThat(result["format"]).isEqualTo("PDF")
    }

    @Test
    fun `getDocument throws for non-existent document`() {
        val exception = assertThrows<NoSuchElementException> {
            client.getDocument("non-existent-doc")
        }
        assertThat(exception.message).contains("Document not found")
    }

    // Edge Cases

    @Test
    fun `generatePdf handles empty sections`() {
        val result = client.generatePdf(
            bidId = "bid-empty",
            title = "Empty Document",
            sections = emptyList(),
            metadata = null,
            templateId = null
        )

        assertThat(result["status"]).isEqualTo("generated")
        val metadata = result["metadata"] as Map<*, *>
        assertThat(metadata["wordCount"]).isEqualTo(0)
    }

    @Test
    fun `checkCompliance handles empty requirements`() {
        val result = client.checkCompliance(
            bidId = "bid-empty-req",
            requirements = emptyList(),
            bidSections = listOf(mapOf("name" to "Section", "content" to "Content"))
        )

        // Should still include standard compliance checks
        val checks = result["checks"] as List<*>
        assertThat(checks).isNotEmpty
    }

    @Test
    fun `validateBrand handles empty sections`() {
        val result = client.validateBrand(
            bidId = "bid-empty-sections",
            bidContent = mapOf(
                "title" to "Test Title",
                "sections" to emptyList<Any>()
            )
        )

        assertThat(result["overallStatus"]).isNotNull
    }
}
