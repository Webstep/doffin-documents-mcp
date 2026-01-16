package no.doffin.documents.mcp.protocol

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.doffin.documents.mcp.client.DocumentsClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class McpRequestHandler(
    private val objectMapper: ObjectMapper,
    private val documentsClient: DocumentsClient
) {
    private val logger = LoggerFactory.getLogger(McpRequestHandler::class.java)

    private val tools = mapOf(
        "ping" to { _: JsonNode ->
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to "pong from documents-mcp")
                )
            )
        },
        "documents.generate.pdf" to { args: JsonNode ->
            val bidId = args.get("bidId")?.asText()
                ?: throw IllegalArgumentException("Parameter 'bidId' is required")
            val title = args.get("title")?.asText()
                ?: throw IllegalArgumentException("Parameter 'title' is required")
            val sections = args.get("sections")?.map { section ->
                mutableMapOf<String, Any>().apply {
                    section.get("name")?.asText()?.let { put("name", it) }
                    section.get("content")?.asText()?.let { put("content", it) }
                    section.get("order")?.asInt()?.let { put("order", it) }
                    section.get("pageBreakBefore")?.asBoolean()?.let { put("pageBreakBefore", it) }
                }.toMap()
            } ?: throw IllegalArgumentException("Parameter 'sections' is required")
            val metadata = args.get("metadata")?.let {
                objectMapper.convertValue(it, Map::class.java) as Map<String, Any>
            }
            val templateId = args.get("templateId")?.asText()

            val result = documentsClient.generatePdf(bidId, title, sections, metadata, templateId)
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to objectMapper.writeValueAsString(result))
                )
            )
        },
        "documents.generate.word" to { args: JsonNode ->
            val bidId = args.get("bidId")?.asText()
                ?: throw IllegalArgumentException("Parameter 'bidId' is required")
            val title = args.get("title")?.asText()
                ?: throw IllegalArgumentException("Parameter 'title' is required")
            val sections = args.get("sections")?.map { section ->
                mutableMapOf<String, Any>().apply {
                    section.get("name")?.asText()?.let { put("name", it) }
                    section.get("content")?.asText()?.let { put("content", it) }
                    section.get("order")?.asInt()?.let { put("order", it) }
                    section.get("pageBreakBefore")?.asBoolean()?.let { put("pageBreakBefore", it) }
                }.toMap()
            } ?: throw IllegalArgumentException("Parameter 'sections' is required")
            val metadata = args.get("metadata")?.let {
                objectMapper.convertValue(it, Map::class.java) as Map<String, Any>
            }
            val templateId = args.get("templateId")?.asText()

            val result = documentsClient.generateWord(bidId, title, sections, metadata, templateId)
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to objectMapper.writeValueAsString(result))
                )
            )
        },
        "documents.compliance.check" to { args: JsonNode ->
            val bidId = args.get("bidId")?.asText()
                ?: throw IllegalArgumentException("Parameter 'bidId' is required")
            val requirements = args.get("requirements")?.map { it.asText() }
                ?: throw IllegalArgumentException("Parameter 'requirements' is required")
            val bidSections = args.get("bidSections")?.map { section ->
                mapOf(
                    "name" to (section.get("name")?.asText() ?: ""),
                    "content" to (section.get("content")?.asText() ?: "")
                )
            } ?: throw IllegalArgumentException("Parameter 'bidSections' is required")

            val result = documentsClient.checkCompliance(bidId, requirements, bidSections)
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to objectMapper.writeValueAsString(result))
                )
            )
        },
        "documents.brand.validate" to { args: JsonNode ->
            val bidId = args.get("bidId")?.asText()
                ?: throw IllegalArgumentException("Parameter 'bidId' is required")
            val bidContent = args.get("bidContent")?.let {
                objectMapper.convertValue(it, Map::class.java) as Map<String, Any>
            } ?: throw IllegalArgumentException("Parameter 'bidContent' is required")

            val result = documentsClient.validateBrand(bidId, bidContent)
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to objectMapper.writeValueAsString(result))
                )
            )
        },
        "documents.template.apply" to { args: JsonNode ->
            val templateId = args.get("templateId")?.asText()
                ?: throw IllegalArgumentException("Parameter 'templateId' is required")
            val bidContent = args.get("bidContent")?.let {
                objectMapper.convertValue(it, Map::class.java) as Map<String, Any>
            } ?: throw IllegalArgumentException("Parameter 'bidContent' is required")

            val result = documentsClient.applyTemplate(templateId, bidContent)
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to objectMapper.writeValueAsString(result))
                )
            )
        },
        "documents.templates.list" to { args: JsonNode ->
            val format = args.get("format")?.asText()

            val result = documentsClient.listTemplates(format)
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to objectMapper.writeValueAsString(result))
                )
            )
        },
        "documents.get" to { args: JsonNode ->
            val documentId = args.get("documentId")?.asText()
                ?: throw IllegalArgumentException("Parameter 'documentId' is required")

            val result = documentsClient.getDocument(documentId)
            mapOf(
                "content" to listOf(
                    mapOf("type" to "text", "text" to objectMapper.writeValueAsString(result))
                )
            )
        }
    )

    fun handle(requestJson: String): String {
        val requestNode = try {
            objectMapper.readTree(requestJson)
        } catch (e: Exception) {
            return createErrorResponse(null, -32700, "Parse error")
        }

        val id = requestNode.get("id")
        val method = requestNode.get("method")?.asText()

        if (method == null) {
            return createErrorResponse(id, -32600, "Invalid Request")
        }

        logger.info("Handling MCP request: method=$method, id=$id")

        return when (method) {
            "initialize" -> handleInitialize(id)
            "initialized" -> handleInitialized(id)
            "tools/list" -> handleListTools(id)
            "tools/call" -> handleCallTool(id, requestNode.get("params"))
            else -> createErrorResponse(id, -32601, "Method not found")
        }
    }

    private fun handleInitialize(id: JsonNode): String {
        logger.info("Client initializing MCP connection")
        val response = mapOf(
            "protocolVersion" to "0.1.0",
            "serverInfo" to mapOf(
                "name" to "doffin-documents-mcp",
                "version" to "0.1.0"
            ),
            "capabilities" to mapOf(
                "tools" to mapOf<String, Any>()
            )
        )
        return createSuccessResponse(id, response)
    }

    private fun handleInitialized(id: JsonNode?): String {
        logger.info("Client confirmed initialization complete")
        return createSuccessResponse(id, mapOf<String, Any>())
    }

    private fun handleListTools(id: JsonNode): String {
        val toolList = listOf(
            mapOf(
                "name" to "ping",
                "description" to "Health check - responds with 'pong from documents-mcp'",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>(),
                    "required" to emptyList<String>()
                )
            ),
            mapOf(
                "name" to "documents.generate.pdf",
                "description" to "Generate a PDF document from bid content. Returns document metadata including file size, page count, and sections.",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "bidId" to mapOf(
                            "type" to "string",
                            "description" to "The bid identifier"
                        ),
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "Document title"
                        ),
                        "sections" to mapOf(
                            "type" to "array",
                            "description" to "Document sections with name, content, order, and optional pageBreakBefore",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "name" to mapOf("type" to "string"),
                                    "content" to mapOf("type" to "string"),
                                    "order" to mapOf("type" to "integer"),
                                    "pageBreakBefore" to mapOf("type" to "boolean")
                                )
                            )
                        ),
                        "metadata" to mapOf(
                            "type" to "object",
                            "description" to "Document metadata (author, company, version, language)"
                        ),
                        "templateId" to mapOf(
                            "type" to "string",
                            "description" to "Optional template ID to use for styling"
                        )
                    ),
                    "required" to listOf("bidId", "title", "sections")
                )
            ),
            mapOf(
                "name" to "documents.generate.word",
                "description" to "Generate a Word document (.docx) from bid content. Returns document metadata including file size, page count, and sections.",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "bidId" to mapOf(
                            "type" to "string",
                            "description" to "The bid identifier"
                        ),
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "Document title"
                        ),
                        "sections" to mapOf(
                            "type" to "array",
                            "description" to "Document sections with name, content, order, and optional pageBreakBefore",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "name" to mapOf("type" to "string"),
                                    "content" to mapOf("type" to "string"),
                                    "order" to mapOf("type" to "integer"),
                                    "pageBreakBefore" to mapOf("type" to "boolean")
                                )
                            )
                        ),
                        "metadata" to mapOf(
                            "type" to "object",
                            "description" to "Document metadata (author, company, version, language)"
                        ),
                        "templateId" to mapOf(
                            "type" to "string",
                            "description" to "Optional template ID to use for styling"
                        )
                    ),
                    "required" to listOf("bidId", "title", "sections")
                )
            ),
            mapOf(
                "name" to "documents.compliance.check",
                "description" to "Check bid content against tender requirements. Returns compliance status, score, and recommendations for missing requirements.",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "bidId" to mapOf(
                            "type" to "string",
                            "description" to "The bid identifier"
                        ),
                        "requirements" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "string"),
                            "description" to "List of tender requirements to check against"
                        ),
                        "bidSections" to mapOf(
                            "type" to "array",
                            "description" to "Bid sections with name and content",
                            "items" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "name" to mapOf("type" to "string"),
                                    "content" to mapOf("type" to "string")
                                )
                            )
                        )
                    ),
                    "required" to listOf("bidId", "requirements", "bidSections")
                )
            ),
            mapOf(
                "name" to "documents.brand.validate",
                "description" to "Validate bid content against brand guidelines. Checks company name usage, forbidden terms, required sections, and title format.",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "bidId" to mapOf(
                            "type" to "string",
                            "description" to "The bid identifier"
                        ),
                        "bidContent" to mapOf(
                            "type" to "object",
                            "description" to "Bid content with title and sections array",
                            "properties" to mapOf(
                                "title" to mapOf("type" to "string"),
                                "sections" to mapOf(
                                    "type" to "array",
                                    "items" to mapOf(
                                        "type" to "object",
                                        "properties" to mapOf(
                                            "name" to mapOf("type" to "string"),
                                            "content" to mapOf("type" to "string")
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "required" to listOf("bidId", "bidContent")
                )
            ),
            mapOf(
                "name" to "documents.template.apply",
                "description" to "Apply a document template to bid content. Maps bid sections to template sections and identifies missing required sections.",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "templateId" to mapOf(
                            "type" to "string",
                            "description" to "Template ID to apply"
                        ),
                        "bidContent" to mapOf(
                            "type" to "object",
                            "description" to "Bid content with sections array",
                            "properties" to mapOf(
                                "sections" to mapOf(
                                    "type" to "array",
                                    "items" to mapOf(
                                        "type" to "object",
                                        "properties" to mapOf(
                                            "name" to mapOf("type" to "string"),
                                            "content" to mapOf("type" to "string")
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "required" to listOf("templateId", "bidContent")
                )
            ),
            mapOf(
                "name" to "documents.templates.list",
                "description" to "List available document templates, optionally filtered by format (PDF, WORD, HTML).",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "format" to mapOf(
                            "type" to "string",
                            "description" to "Filter by document format: PDF, WORD, or HTML"
                        )
                    ),
                    "required" to emptyList<String>()
                )
            ),
            mapOf(
                "name" to "documents.get",
                "description" to "Get a generated document by its ID. Returns document metadata and section information.",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "documentId" to mapOf(
                            "type" to "string",
                            "description" to "The document identifier"
                        )
                    ),
                    "required" to listOf("documentId")
                )
            )
        )
        return createSuccessResponse(id, mapOf("tools" to toolList))
    }

    private fun handleCallTool(id: JsonNode, params: JsonNode?): String {
        val name = params?.get("name")?.asText()
        if (name == null || !tools.containsKey(name)) {
            return createErrorResponse(id, -32601, "Tool not found: $name")
        }

        val arguments = params.get("arguments") ?: objectMapper.createObjectNode()
        return try {
            val result = tools[name]!!.invoke(arguments)
            createSuccessResponse(id, result)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid parameters for tool '$name': ${e.message}", e)
            createErrorResponse(id, -32602, "Invalid params: ${e.message}")
        } catch (e: NoSuchElementException) {
            logger.warn("Resource not found for tool '$name': ${e.message}", e)
            createErrorResponse(id, -32602, "Not found: ${e.message}")
        } catch (e: Exception) {
            logger.error("Internal error executing tool '$name'", e)
            createErrorResponse(id, -32603, "Internal error: ${e.message}")
        }
    }

    private fun createSuccessResponse(id: JsonNode?, result: Any): String {
        val response = objectMapper.createObjectNode()
        response.put("jsonrpc", "2.0")
        if (id != null) response.set<JsonNode>("id", id)
        response.set<JsonNode>("result", objectMapper.valueToTree(result))
        return objectMapper.writeValueAsString(response)
    }

    private fun createErrorResponse(id: JsonNode?, code: Int, message: String): String {
        val response = objectMapper.createObjectNode()
        response.put("jsonrpc", "2.0")
        if (id != null) response.set<JsonNode>("id", id) else response.putNull("id")
        val error = response.putObject("error")
        error.put("code", code)
        error.put("message", message)
        return objectMapper.writeValueAsString(response)
    }
}
