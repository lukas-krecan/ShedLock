package net.javacrumbs.shedlock.provider.exposed

import java.sql.SQLIntegrityConstraintViolationException
import java.time.Duration
import java.time.LocalDateTime
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.provider.exposed.Shedlock.lockUntil
import net.javacrumbs.shedlock.provider.exposed.Shedlock.lockedAt
import net.javacrumbs.shedlock.provider.exposed.Shedlock.lockedBy
import net.javacrumbs.shedlock.provider.exposed.Shedlock.name
import net.javacrumbs.shedlock.support.AbstractStorageAccessor
import net.javacrumbs.shedlock.support.LockException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import java.sql.SQLException

internal class ExposedStorageAccessor(private val database: Database) : AbstractStorageAccessor() {

    private val now: Expression<LocalDateTime> = CurrentDateTime

    override fun insertRecord(lockConfiguration: LockConfiguration): Boolean =
            transaction(database) {
                try {
                    if (database.dialect is PostgreSQLDialect) {
                        Shedlock.upsert(
                                onUpdate = {
                                    it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                                    it[lockedAt] = now
                                    it[lockedBy] = hostname
                                },
                                body = {
                                    it[name] = lockConfiguration.name
                                    it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                                    it[lockedAt] = now
                                    it[lockedBy] = hostname
                                },
                                where = { (name eq lockConfiguration.name) and (lockUntil lessEq now) }
                        ).insertedCount > 0
                    } else {
                        Shedlock.insert {
                            it[name] = lockConfiguration.name
                            it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                            it[lockedAt] = now
                            it[lockedBy] = hostname
                        }.insertedCount > 0
                    }
                } catch (e: ExposedSQLException) {
                    if (e.cause is SQLIntegrityConstraintViolationException) {
                        // lock record already exists
                        false
                    } else {
                        logger.debug("ExposedSQLException thrown when inserting record", e)
                        throw LockException("Unexpected exception when locking", e)
                    }
                } catch (e: Exception) {
                    logger.debug("Exception thrown when inserting record", e)
                    throw LockException("Unexpected exception when locking", e)
                }
            }

    override fun updateRecord(lockConfiguration: LockConfiguration): Boolean =
            transaction(database) {
                try {
                    Shedlock.update({ (name eq lockConfiguration.name) and (lockUntil lessEq now) }) {
                        it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                        it[lockedAt] = now
                        it[lockedBy] = hostname
                    } > 0
                } catch (e: Exception) {
                    throw LockException("Unexpected exception when locking", e)
                }
            }

    override fun unlock(lockConfiguration: LockConfiguration) {
        transaction(database) {
            try {
                val lockAtLeastUntil = Shedlock.lockedAt.plus(lockConfiguration.lockAtLeastFor)

                Shedlock.update({ (name eq lockConfiguration.name) and (lockedBy eq hostname) }) {
                    it[lockUntil] = Case().When(lockAtLeastUntil greater now, lockAtLeastUntil).Else(now)
                }
            } catch (e: Exception) {
                throw LockException("Unexpected exception when unlocking", e)
            }
        }
    }

    override fun extend(lockConfiguration: LockConfiguration): Boolean =
            transaction(database) {
                try {
                    Shedlock.update({
                        (name eq lockConfiguration.name) and (lockedBy eq hostname) and (lockUntil greater now)
                    }) {
                        it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                    } > 0
                } catch (e: Exception) {
                    throw LockException("Unexpected exception when extending", e)
                }
            }

    private fun nowPlus(duration: Duration): Expression<LocalDateTime> = now.plus(duration)
}
