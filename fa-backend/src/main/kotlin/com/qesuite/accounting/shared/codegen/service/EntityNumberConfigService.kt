package com.qesuite.accounting.shared.codegen.service

import com.qesuite.accounting.shared.codegen.domain.EntityNumberConfig
import com.qesuite.accounting.shared.codegen.domain.NumberingModule
import com.qesuite.accounting.shared.codegen.repository.EntityNumberConfigRepository
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Year
import java.util.UUID

data class PrefixConfig(
    val prefix: String,
    val yearScoped: Boolean,
    val padWidth: Int = 4,
    val customFormat: String? = null,
)

data class NumberConfigDto(
    val moduleKey: String,
    val label: String,
    val prefix: String,
    val allowedPrefixes: List<String>,
    val yearScoped: Boolean,
    val format: String,
    val example: String,
    val resets: String,
    val customFormat: String?,
)

@Service
class EntityNumberConfigService(
    private val repo: EntityNumberConfigRepository,
) {

    @Transactional(readOnly = true)
    fun resolveConfig(entityId: UUID, moduleKey: String): PrefixConfig {
        val mod    = NumberingModule.fromKey(moduleKey)
        val saved  = repo.findByEntityIdAndModuleKey(entityId, moduleKey)
        val prefix = saved?.prefix ?: mod?.defaultPrefix ?: moduleKey
        return PrefixConfig(
            prefix       = prefix,
            yearScoped   = mod?.yearScoped ?: false,
            customFormat = saved?.customFormat,
        )
    }

    @Transactional(readOnly = true)
    fun getAll(entityId: UUID): List<NumberConfigDto> {
        val saved = repo.findByEntityId(entityId).associateBy { it.moduleKey }
        return NumberingModule.entries.map { mod ->
            val prefix       = saved[mod.moduleKey]?.prefix ?: mod.defaultPrefix
            val customFormat = saved[mod.moduleKey]?.customFormat
            val fmt          = customFormat ?: defaultFormat(prefix, mod.yearScoped)
            NumberConfigDto(
                moduleKey       = mod.moduleKey,
                label           = mod.label,
                prefix          = prefix,
                allowedPrefixes = mod.allowedPrefixes,
                yearScoped      = mod.yearScoped,
                format          = fmt,
                example         = applyFormatExample(fmt, prefix, mod.yearScoped),
                resets          = if (mod.yearScoped) "Yearly" else "Never",
                customFormat    = customFormat,
            )
        }
    }

    @Transactional
    fun update(entityId: UUID, moduleKey: String, prefix: String, customFormat: String?): NumberConfigDto {
        val mod = NumberingModule.fromKey(moduleKey)
            ?: throw ValidationException("UNKNOWN_MODULE", "Module $moduleKey is not registered.")
        val upperPrefix = prefix.uppercase().trim()
        if (upperPrefix !in mod.allowedPrefixes) {
            throw ValidationException(
                "INVALID_PREFIX",
                "Prefix $upperPrefix is not allowed for $moduleKey. Allowed: ${mod.allowedPrefixes}",
            )
        }
        if (customFormat != null && !customFormat.contains(Regex("\\{0+\\}"))) {
            throw ValidationException(
                "INVALID_FORMAT",
                "Format must contain a sequence token such as {0000}.",
            )
        }
        val config = repo.findByEntityIdAndModuleKey(entityId, moduleKey)
            ?: EntityNumberConfig(entityId = entityId, moduleKey = moduleKey, prefix = upperPrefix)
        config.prefix       = upperPrefix
        config.customFormat = customFormat?.trim()?.ifBlank { null }
        repo.save(config)
        val fmt = config.customFormat ?: defaultFormat(upperPrefix, mod.yearScoped)
        return NumberConfigDto(
            moduleKey       = mod.moduleKey,
            label           = mod.label,
            prefix          = upperPrefix,
            allowedPrefixes = mod.allowedPrefixes,
            yearScoped      = mod.yearScoped,
            format          = fmt,
            example         = applyFormatExample(fmt, upperPrefix, mod.yearScoped),
            resets          = if (mod.yearScoped) "Yearly" else "Never",
            customFormat    = config.customFormat,
        )
    }

    companion object {
        fun defaultFormat(prefix: String, yearScoped: Boolean) =
            if (yearScoped) "{PREFIX}-{YYYY}-{0000}" else "{PREFIX}{0000}"

        fun applyFormatExample(format: String, prefix: String, yearScoped: Boolean): String {
            val year = Year.now().value
            val padWidth = Regex("\\{(0+)\\}").find(format)?.groupValues?.get(1)?.length ?: 4
            val seqStr = "1".padStart(padWidth, '0')
            return format
                .replace("{PREFIX}", prefix)
                .replace("{YYYY}", if (yearScoped) year.toString() else "")
                .replace("{YY}", if (yearScoped) (year % 100).toString().padStart(2, '0') else "")
                .replace(Regex("\\{0+\\}"), seqStr)
        }
    }
}
