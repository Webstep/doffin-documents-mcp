package no.doffin.documents.mcp.protocol

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mcp")
class McpProtocolController(private val mcpRequestHandler: McpRequestHandler) {

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun handleRequest(@RequestBody requestBody: String): ResponseEntity<String> {
        val response = mcpRequestHandler.handle(requestBody)
        return ResponseEntity.ok(response)
    }
}
