package no.doffin.documents.mcp.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    @Value("\${cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private val allowedOriginsConfig: String
) : WebMvcConfigurer {

    private val allowedOrigins: Array<String>
        get() = allowedOriginsConfig.split(",").map { it.trim() }.toTypedArray()

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/mcp")
            .allowedOrigins(*allowedOrigins)
            .allowedMethods("POST", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}
