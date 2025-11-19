package net.javacrumbs.shedlock.provider.exposed

import java.sql.SQLException
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
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

internal class ExposedStorageAccessor(private val database: Database) : AbstractStorageAccessor() {

    private val now: Expression<LocalDateTime> = CurrentTimestampExact

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
                            where = { (name eq lockConfiguration.name) and (lockUntil lessEq now) },
                        )
                        .insertedCount > 0
                } else {
                    Shedlock.insert {
                            it[name] = lockConfiguration.name
                            it[lockUntil] = nowPlus(lockConfiguration.lockAtMostFor)
                            it[lockedAt] = now
                            it[lockedBy] = hostname
                        }
                        .insertedCount > 0
                }
            } catch (e: ExposedSQLException) {
                when (e.cause) {
                    is SQLIntegrityConstraintViolationException -> false
                    is SQLException if ((e.cause as SQLException).sqlState.isConstraintViolation()) -> {
                        logger.debug("Constraint violation, duplicate key error is expected here {}", e.message)
                        false
                    }
                    else -> {
                        logger.debug("ExposedSQLException thrown when inserting record", e)
                        throw LockException("Unexpected exception when locking", e)
                    }
                }
            } catch (e: Exception) {
                logger.debug("Exception thrown when inserting record", e)
                throw LockException("Unexpected exception when locking", e)
            }
        }

    private fun String?.isConstraintViolation(): Boolean {
        return this?.startsWith("23") ?: false
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

// Support for exact current timestamp in MsSQL server. Exposed's CurrentDateTime uses CURRENT_TIMESTAMP which has lower
// precission. Inspired by Exposed's CurrentDateTime implementation.
private object CurrentTimestampExact : Expression<LocalDateTime>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        +when {
            (currentDialect as? MysqlDialect)?.isFractionDateTimeSupported() == true -> "CURRENT_TIMESTAMP(6)"
            currentDialect is SQLServerDialect -> "SYSUTCDATETIME()"
            else -> "CURRENT_TIMESTAMP"
        }
    }
}
