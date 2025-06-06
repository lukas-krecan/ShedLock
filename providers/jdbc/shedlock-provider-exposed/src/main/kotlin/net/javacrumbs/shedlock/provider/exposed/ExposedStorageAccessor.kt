package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.support.AbstractStorageAccessor
import net.javacrumbs.shedlock.support.LockException
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.time.Duration
import java.time.LocalDateTime


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
            } catch (_: SQLIntegrityConstraintViolationException) {
                // lock record already exists
                false
            } catch (e: SQLException) {
                logger.debug("Exception thrown when inserting record", e)
                false
            }
        }

    override fun updateRecord(lockConfiguration: LockConfiguration): Boolean =
        transaction(database) {
            try {
                Shedlock.update({ (Shedlock.name eq lockConfiguration.name) and (Shedlock.lockUntil lessEq now) }) {
                    it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                    it[lockedAt] = now
                    it[lockedBy] = hostname
                } > 0
            } catch (e: SQLException) {
                throw LockException("Unexpected exception when locking", e)
            }
        }

    override fun unlock(lockConfiguration: LockConfiguration) {
        transaction(database) {
            try {
                val lockAtLeastUntil = Shedlock.lockedAt.plus(lockConfiguration.lockAtLeastFor)

                Shedlock.update({ (Shedlock.name eq lockConfiguration.name) and (Shedlock.lockedBy eq hostname) }) {
                    it[lockUntil] = Case().When(lockAtLeastUntil greater now, lockAtLeastUntil).Else(now)
                }
            } catch (e: SQLException) {
                throw LockException("Unexpected exception when unlocking", e)
            }
        }
    }

    override fun extend(lockConfiguration: LockConfiguration): Boolean =
        transaction(database) {
            try {
                Shedlock.update({
                    (Shedlock.name eq lockConfiguration.name) and
                            (Shedlock.lockedBy eq hostname) and
                            (Shedlock.lockUntil greater now)
                }) {
                    it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                } > 0
            } catch (e: SQLException) {
                throw LockException("Unexpected exception when extending", e)
            }
        }

    private fun nowPlus(duration: Duration): Expression<LocalDateTime> = now.plus(duration)
}
