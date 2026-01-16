package no.doffin.documents.mcp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class DocumentsMcpServerApplication

fun main(args: Array<String>) {
    runApplication<DocumentsMcpServerApplication>(*args)
}
