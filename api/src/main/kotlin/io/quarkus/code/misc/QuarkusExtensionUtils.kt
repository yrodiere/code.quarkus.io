package io.quarkus.code.misc

import com.google.common.collect.Lists
import io.quarkus.code.config.ExtensionProcessorConfig
import io.quarkus.code.model.CodeQuarkusExtension
import io.quarkus.maven.ArtifactCoords
import io.quarkus.platform.catalog.processor.CatalogProcessor.getProcessedCategoriesInOrder
import io.quarkus.platform.catalog.processor.ExtensionProcessor
import io.quarkus.registry.catalog.Category
import io.quarkus.registry.catalog.Extension
import io.quarkus.registry.catalog.ExtensionCatalog
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.pow

object QuarkusExtensionUtils {
    private const val hashAlphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
    private const val hashCharEncodeLength = hashAlphabet.length
    private const val hashMaxLength = 3
    private val maxHashCode: Int

    init {
        var max = 0
        for (i in 1..hashMaxLength) {
            max += hashCharEncodeLength.toDouble().pow(i).toInt()
        }
        maxHashCode = max
    }

    /**
     * This function will shorten the given string with the defined hashAlphabet and with a defined maximum length
     * Collisions are a possibility since only maxHashCode combination are available
     */
    fun shorten(input: String): String {
        var res = abs(input.hashCode()) % maxHashCode
        var hash = ""
        do {
            hash = hashAlphabet[res % hashCharEncodeLength] + hash
            res /= hashCharEncodeLength
        } while (res > 0)
        return hash
    }

    fun toShortcut(id: String): String = id.replace(Regex("^([^:]+:)?(quarkus-)?"), "")

    @JvmStatic
    fun processExtensions(catalog: ExtensionCatalog, config: ExtensionProcessorConfig): List<CodeQuarkusExtension> {
        val list = Lists.newArrayList<CodeQuarkusExtension>()
        val processedCategories = getProcessedCategoriesInOrder(catalog)
        val order = AtomicInteger()
        processedCategories.forEach { c ->
            c.sortedExtensions.forEach { e ->
                val codeQExt = toCodeQuarkusExtension(e, c.category, order, config)
                codeQExt?.let { list.add(it) }
            }
        }
        return list
    }

    @JvmStatic
    fun toCodeQuarkusExtension(
        ext: Extension?,
        cat: Category,
        order: AtomicInteger,
        config: ExtensionProcessorConfig
    ): CodeQuarkusExtension? {
        if (ext == null || ext.name == null) {
            return null
        }
        val extensionProcessor = ExtensionProcessor.of(ext)
        if (extensionProcessor.isUnlisted) {
            return null
        }
        val id = ext.managementKey()
        val shortId = createShortId(id)
        var tags = extensionProcessor.getTags(config.tagsFrom.orElse(null))
            .map { if (it == "provides-code") "code" else it }
        if (tags.isEmpty() || (tags.size == 1 && tags.contains("code"))) {
            tags = tags.plus("stable")
        }
        return CodeQuarkusExtension(
            id = id,
            shortId = shortId,
            version = ext.artifact.version,
            name = ext.name,
            description = ext.description,
            shortName = extensionProcessor.shortName,
            category = cat.name,
            tags = tags,
            keywords = extensionProcessor.extendedKeywords,
            order = order.getAndIncrement(),
            providesExampleCode = extensionProcessor.providesCode(),
            providesCode = extensionProcessor.providesCode(),
            guide = extensionProcessor.guide,
            platform = ext.hasPlatformOrigin(),
            bom = getBom(ext)?.let { "${it.groupId}:${it.artifactId}:${it.version}" }
        )
    }

    private fun getBom(extension: Extension): ArtifactCoords? {
        return if (extension.origins == null || extension.origins.isEmpty()) {
            null
        } else extension.origins[0].bom
    }

    internal fun createShortId(id: String): String {
        val normalized = id.replace("^(io.quarkus:)?quarkus-".toRegex(), "")
        return shorten(normalized)
    }
}