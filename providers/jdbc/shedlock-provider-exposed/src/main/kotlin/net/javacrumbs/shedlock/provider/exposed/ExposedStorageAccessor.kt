package net.javacrumbs.shedlock.provider.exposed

import java.time.Duration
import java.time.LocalDateTime
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.support.AbstractStorageAccessor
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

internal class ExposedStorageAccessor(private val database: Database) : AbstractStorageAccessor() {

    private val now: Expression<LocalDateTime> = CurrentDateTime

    override fun insertRecord(lockConfiguration: LockConfiguration): Boolean =
        transaction(database) {
            try {
                Shedlock.insert {
                    it[name] = lockConfiguration.name
                    it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                    it[lockedAt] = now
                    it[lockedBy] = hostname
                }
                true
            } catch (_: Exception) {
                false
            }
        }

    override fun updateRecord(lockConfiguration: LockConfiguration): Boolean =
        transaction(database) {
            Shedlock.update({ (Shedlock.name eq lockConfiguration.name) and (Shedlock.lockUntil lessEq now) }) {
                it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                it[lockedAt] = now
                it[lockedBy] = hostname
            } > 0
        }

    override fun unlock(lockConfiguration: LockConfiguration) {
        transaction(database) {
            val lockAtLeastUntil = Shedlock.lockedAt.plus(lockConfiguration.lockAtLeastFor)

            Shedlock.update({ (Shedlock.name eq lockConfiguration.name) and (Shedlock.lockedBy eq hostname) }) {
                it[lockUntil] = Case().When(lockAtLeastUntil greater now, lockAtLeastUntil).Else(now)
            }
        }
    }

    override fun extend(lockConfiguration: LockConfiguration): Boolean =
        transaction(database) {
            Shedlock.update({
                (Shedlock.name eq lockConfiguration.name) and
                    (Shedlock.lockedBy eq hostname) and
                    (Shedlock.lockUntil greater now)
            }) {
                it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
            } > 0
        }

    private fun nowPlus(duration: Duration): Expression<LocalDateTime> = now.plus(duration)
}
