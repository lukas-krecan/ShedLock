package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.support.StorageBasedLockProvider
import org.jetbrains.exposed.v1.jdbc.Database

class ExposedLockProvider(database: Database) : StorageBasedLockProvider(ExposedStorageAccessor(database))
