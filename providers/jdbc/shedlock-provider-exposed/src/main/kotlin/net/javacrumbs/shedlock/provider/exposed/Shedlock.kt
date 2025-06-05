package net.javacrumbs.shedlock.provider.exposed

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Shedlock : Table() {
    override val tableName: String = "shedlock"
    val name = varchar("name", 64)
    val lockUntil = datetime("lock_until")
    val lockedAt = datetime("locked_at")
    val lockedBy = varchar("locked_by", 255)

    override val primaryKey = PrimaryKey(name)
}
