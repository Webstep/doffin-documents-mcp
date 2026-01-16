package no.doffin.documents.mcp.protocol

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.doffin.documents.mcp.client.DocumentsClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpRequestHandlerTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var documentsClient: DocumentsClient
    private lateinit var handler: McpRequestHandler

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
        objectMapper.findAndRegisterModules() // For LocalDateTime support
        documentsClient = DocumentsClient(
            mockEnabled = true,
            outputDir = "./test-documents",
            objectMapper = objectMapper
        )
        documentsClient.init()
        handler = McpRequestHandler(objectMapper, documentsClient)
    }

    @Test
    fun `initialize returns protocol version and capabilities`() {
        val request = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.get("jsonrpc").asText()).isEqualTo("2.0")
        assertThat(json.get("id").asInt()).isEqualTo(1)
        assertThat(json.get("result").get("protocolVersion").asText()).isEqualTo("0.1.0")
        assertThat(json.get("result").get("serverInfo").get("name").asText()).isEqualTo("doffin-documents-mcp")
    }

    @Test
    fun `tools list returns all available tools`() {
        val request = """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        val tools = json.get("result").get("tools")
        assertThat(tools.isArray).isTrue()
        assertThat(tools.size()).isEqualTo(8)

        val toolNames = tools.map { it.get("name").asText() }
        assertThat(toolNames).containsExactlyInAnyOrder(
            "ping",
            "documents.generate.pdf",
            "documents.generate.word",
            "documents.compliance.check",
            "documents.brand.validate",
            "documents.template.apply",
            "documents.templates.list",
            "documents.get"
        )
    }

    @Test
    fun `ping tool returns pong`() {
        val request = """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"ping","arguments":{}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        val content = json.get("result").get("content")
        assertThat(content[0].get("text").asText()).isEqualTo("pong from documents-mcp")
    }

    @Test
    fun `documents generate pdf requires bidId, title, and sections`() {
        val request = """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"documents.generate.pdf","arguments":{}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("code").asInt()).isEqualTo(-32602)
        assertThat(json.get("error").get("message").asText()).contains("bidId")
    }

    @Test
    fun `documents generate pdf creates document`() {
        val request = """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"documents.generate.pdf","arguments":{"bidId":"bid-001","title":"Test Bid Document","sections":[{"name":"Executive Summary","content":"This is the executive summary for our bid.","order":1},{"name":"Solution","content":"Our proposed solution includes...","order":2}]}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("result")).isTrue()
        val content = json.get("result").get("content")
        val docJson = objectMapper.readTree(content[0].get("text").asText())
        assertThat(docJson.get("documentId").asText()).startsWith("doc-")
        assertThat(docJson.get("bidId").asText()).isEqualTo("bid-001")
        assertThat(docJson.get("format").asText()).isEqualTo("PDF")
        assertThat(docJson.get("fileName").asText()).endsWith(".pdf")
        assertThat(docJson.has("metadata")).isTrue()
        assertThat(docJson.get("metadata").get("pageCount").asInt()).isGreaterThan(0)
    }

    @Test
    fun `documents generate word creates document`() {
        val request = """{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"documents.generate.word","arguments":{"bidId":"bid-002","title":"Test Bid Word Doc","sections":[{"name":"Introduction","content":"Introduction text here.","order":1}]}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("result")).isTrue()
        val content = json.get("result").get("content")
        val docJson = objectMapper.readTree(content[0].get("text").asText())
        assertThat(docJson.get("format").asText()).isEqualTo("WORD")
        assertThat(docJson.get("fileName").asText()).endsWith(".docx")
    }

    @Test
    fun `documents compliance check requires bidId, requirements, and bidSections`() {
        val request = """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"documents.compliance.check","arguments":{"bidId":"bid-001"}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("code").asInt()).isEqualTo(-32602)
    }

    @Test
    fun `documents compliance check returns compliance result`() {
        val request = """{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"documents.compliance.check","arguments":{"bidId":"bid-001","requirements":["security","team experience","project methodology"],"bidSections":[{"name":"Security","content":"We implement ISO 27001 security standards."},{"name":"Team","content":"Our team has 10 years experience."}]}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("result")).isTrue()
        val content = json.get("result").get("content")
        val checkJson = objectMapper.readTree(content[0].get("text").asText())
        assertThat(checkJson.get("bidId").asText()).isEqualTo("bid-001")
        assertThat(checkJson.has("overallStatus")).isTrue()
        assertThat(checkJson.has("score")).isTrue()
        assertThat(checkJson.has("checks")).isTrue()
        assertThat(checkJson.has("recommendations")).isTrue()
    }

    @Test
    fun `documents brand validate requires bidId and bidContent`() {
        val request = """{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"documents.brand.validate","arguments":{}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("message").asText()).contains("bidId")
    }

    @Test
    fun `documents brand validate returns validation result`() {
        val request = """{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"documents.brand.validate","arguments":{"bidId":"bid-001","bidContent":{"title":"Webstep Bid for Cloud Services","sections":[{"name":"Company Profile","content":"Webstep is a leading IT consultancy."},{"name":"Contact Information","content":"Email: info@webstep.no"}]}}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("result")).isTrue()
        val content = json.get("result").get("content")
        val brandJson = objectMapper.readTree(content[0].get("text").asText())
        assertThat(brandJson.get("bidId").asText()).isEqualTo("bid-001")
        assertThat(brandJson.has("overallStatus")).isTrue()
        assertThat(brandJson.has("score")).isTrue()
        assertThat(brandJson.has("checks")).isTrue()
    }

    @Test
    fun `documents template apply requires templateId and bidContent`() {
        val request = """{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"documents.template.apply","arguments":{}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("message").asText()).contains("templateId")
    }

    @Test
    fun `documents template apply returns applied template`() {
        val request = """{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"documents.template.apply","arguments":{"templateId":"template-pdf-standard","bidContent":{"sections":[{"name":"Executive Summary","content":"This is our executive summary."},{"name":"Company Profile","content":"We are Webstep."}]}}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("result")).isTrue()
        val content = json.get("result").get("content")
        val templateJson = objectMapper.readTree(content[0].get("text").asText())
        assertThat(templateJson.get("templateId").asText()).isEqualTo("template-pdf-standard")
        assertThat(templateJson.get("templateName").asText()).contains("PDF")
        assertThat(templateJson.has("sections")).isTrue()
        assertThat(templateJson.has("summary")).isTrue()
    }

    @Test
    fun `documents template apply returns error for invalid template`() {
        val request = """{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"documents.template.apply","arguments":{"templateId":"non-existent","bidContent":{"sections":[]}}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("message").asText()).contains("not found")
    }

    @Test
    fun `documents templates list returns all templates`() {
        val request = """{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"documents.templates.list","arguments":{}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("result")).isTrue()
        val content = json.get("result").get("content")
        val listJson = objectMapper.readTree(content[0].get("text").asText())
        assertThat(listJson.has("templates")).isTrue()
        assertThat(listJson.get("total").asInt()).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `documents templates list filters by format`() {
        val request = """{"jsonrpc":"2.0","id":15,"method":"tools/call","params":{"name":"documents.templates.list","arguments":{"format":"PDF"}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("result")).isTrue()
        val content = json.get("result").get("content")
        val listJson = objectMapper.readTree(content[0].get("text").asText())
        val templates = listJson.get("templates")
        templates.forEach { template ->
            assertThat(template.get("format").asText()).isEqualTo("PDF")
        }
    }

    @Test
    fun `documents get requires documentId`() {
        val request = """{"jsonrpc":"2.0","id":16,"method":"tools/call","params":{"name":"documents.get","arguments":{}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("message").asText()).contains("documentId")
    }

    @Test
    fun `documents get returns document for generated id`() {
        // First generate a document
        val generateRequest = """{"jsonrpc":"2.0","id":17,"method":"tools/call","params":{"name":"documents.generate.pdf","arguments":{"bidId":"bid-get-test","title":"Test Document","sections":[{"name":"Section 1","content":"Content","order":1}]}}}"""
        val generateResponse = handler.handle(generateRequest)
        val generateJson = objectMapper.readTree(generateResponse)
        val generateContent = generateJson.get("result").get("content")
        val generatedDoc = objectMapper.readTree(generateContent[0].get("text").asText())
        val documentId = generatedDoc.get("documentId").asText()

        // Then get it
        val getRequest = """{"jsonrpc":"2.0","id":18,"method":"tools/call","params":{"name":"documents.get","arguments":{"documentId":"$documentId"}}}"""
        val getResponse = handler.handle(getRequest)
        val getJson = objectMapper.readTree(getResponse)

        assertThat(getJson.has("result")).isTrue()
        val content = getJson.get("result").get("content")
        val docJson = objectMapper.readTree(content[0].get("text").asText())
        assertThat(docJson.get("documentId").asText()).isEqualTo(documentId)
        assertThat(docJson.get("bidId").asText()).isEqualTo("bid-get-test")
    }

    @Test
    fun `documents get returns error for non-existent document`() {
        val request = """{"jsonrpc":"2.0","id":19,"method":"tools/call","params":{"name":"documents.get","arguments":{"documentId":"non-existent"}}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("message").asText()).contains("not found")
    }

    @Test
    fun `unknown method returns error`() {
        val request = """{"jsonrpc":"2.0","id":20,"method":"unknown/method","params":{}}"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("code").asInt()).isEqualTo(-32601)
    }

    @Test
    fun `invalid JSON returns parse error`() {
        val request = """not valid json"""

        val response = handler.handle(request)
        val json = objectMapper.readTree(response)

        assertThat(json.has("error")).isTrue()
        assertThat(json.get("error").get("code").asInt()).isEqualTo(-32700)
    }
}
