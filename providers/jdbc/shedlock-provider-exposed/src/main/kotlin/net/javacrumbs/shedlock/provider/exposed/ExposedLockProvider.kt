package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.support.StorageBasedLockProvider
import org.jetbrains.exposed.sql.Database

class ExposedLockProvider(database: Database) : StorageBasedLockProvider(ExposedStorageAccessor(database))
