package net.javacrumbs.shedlock.provider.exposed

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.datetime

internal object Shedlock : Table() {
    override val tableName: String = "shedlock"
    val name = varchar("name", 64)
    val lockUntil = datetime("lock_until")
    val lockedAt = datetime("locked_at")
    val lockedBy = varchar("locked_by", 255)

    override val primaryKey = PrimaryKey(name)
}
