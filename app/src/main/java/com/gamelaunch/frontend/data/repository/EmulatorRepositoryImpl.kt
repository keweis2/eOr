package com.gamelaunch.frontend.data.repository

import com.gamelaunch.frontend.data.db.dao.EmulatorMappingDao
import com.gamelaunch.frontend.data.db.entity.EmulatorMappingEntity
import com.gamelaunch.frontend.domain.model.EmulatorMapping
import com.gamelaunch.frontend.domain.model.InstalledEmulator
import com.gamelaunch.frontend.domain.platform.PlatformCatalog
import com.gamelaunch.frontend.domain.platform.PlatformDefinitions
import com.gamelaunch.frontend.domain.repository.EmulatorRepository
import com.gamelaunch.frontend.launcher.PackageManagerHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorRepositoryImpl @Inject constructor(
    private val emulatorMappingDao: EmulatorMappingDao,
    private val packageManagerHelper: PackageManagerHelper
) : EmulatorRepository {

    private val gson = Gson()

    override suspend fun getMappingForPlatform(platformId: String): EmulatorMapping? =
        emulatorMappingDao.getMappingForPlatform(platformId)?.let { entityToDomain(it) }

    override fun getAllMappings(): Flow<List<EmulatorMapping>> =
        emulatorMappingDao.getAllMappings().map { list -> list.map { entityToDomain(it) } }

    override suspend fun upsertMapping(mapping: EmulatorMapping) {
        val entity = domainToEntity(mapping)
        val finalEntity = if (entity.id == 0L) {
            val existing = emulatorMappingDao.getMappingForPlatform(entity.platformId)
            if (existing != null) {
                entity.copy(id = existing.id)
            } else {
                entity
            }
        } else {
            entity
        }
        emulatorMappingDao.upsertMapping(finalEntity)
    }

    override suspend fun deleteMappingForPlatform(platformId: String) {
        emulatorMappingDao.deleteMappingForPlatform(platformId)
    }

    override fun getInstalledEmulators(): List<InstalledEmulator> =
        packageManagerHelper.getInstalledEmulators()

    override suspend fun autoDetectAndAssign(): Int = assign(onlyMissing = false)

    override suspend fun assignMissing(): Int = assign(onlyMissing = true)

    override suspend fun followCatalogCores(before: PlatformCatalog, after: PlatformCatalog): Int {
        var updated = 0
        after.platforms.forEach { platform ->
            val oldCore = before.byId[platform.id]?.defaultCoreForRetroArch ?: return@forEach
            val newCore = platform.defaultCoreForRetroArch ?: return@forEach
            if (oldCore == newCore) return@forEach
            val mapping = emulatorMappingDao.getMappingForPlatform(platform.id) ?: return@forEach
            if (!mapping.isRetroArch || mapping.retroArchCore != oldCore) return@forEach
            emulatorMappingDao.upsertMapping(mapping.copy(retroArchCore = newCore))
            updated++
        }
        return updated
    }

    private suspend fun assign(onlyMissing: Boolean): Int {
        val installedPkgs = packageManagerHelper.getInstalledEmulators()
            .filter { it.isInstalled }
            .map { it.packageName }
            .toSet()

        var configured = 0
        PlatformDefinitions.ALL.forEach { platform ->
            val existing = emulatorMappingDao.getMappingForPlatform(platform.id)
            if (onlyMissing && existing != null) return@forEach
            val priority = packageManagerHelper.platformEmulatorPriority[platform.id]
                ?: listOf("com.retroarch.aarch64", "org.libretro.retroarch")
            val chosen = priority.firstOrNull { it in installedPkgs } ?: return@forEach
            val isRetroArch = chosen in packageManagerHelper.retroArchPackages

            // @Upsert matches on the primary key `id`, not the unique platform_id index.
            // Reuse the existing row's id so the UPDATE path actually overwrites it;
            // a fresh id=0 would conflict on the unique index and silently no-op.
            val existingId = existing?.id ?: 0L
            emulatorMappingDao.upsertMapping(
                EmulatorMappingEntity(
                    id = existingId,
                    platformId = platform.id,
                    packageName = chosen,
                    isRetroArch = isRetroArch,
                    retroArchCore = if (isRetroArch) platform.defaultCoreForRetroArch else null
                )
            )
            configured++
        }
        return configured
    }

    private fun entityToDomain(entity: EmulatorMappingEntity): EmulatorMapping {
        val extrasType = object : TypeToken<Map<String, String>>() {}.type
        val extras = runCatching<Map<String, String>> {
            gson.fromJson(entity.intentExtrasJson, extrasType) ?: emptyMap()
        }.getOrDefault(emptyMap())

        return EmulatorMapping(
            id = entity.id,
            platformId = entity.platformId,
            packageName = entity.packageName,
            launchAction = entity.launchAction,
            intentExtras = extras,
            isRetroArch = entity.isRetroArch,
            retroArchCore = entity.retroArchCore
        )
    }

    private fun domainToEntity(mapping: EmulatorMapping) = EmulatorMappingEntity(
        id = mapping.id,
        platformId = mapping.platformId,
        packageName = mapping.packageName,
        launchAction = mapping.launchAction,
        intentExtrasJson = gson.toJson(mapping.intentExtras),
        isRetroArch = mapping.isRetroArch,
        retroArchCore = mapping.retroArchCore
    )
}
