package net.javacrumbs.shedlock.provider.exposed

import java.time.Duration
import java.time.LocalDateTime
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect

internal fun Expression<LocalDateTime>.plus(duration: Duration): Expression<LocalDateTime> =
    IntervalExpression(this, duration)

private class IntervalExpression(private val expression: Expression<LocalDateTime>, private val duration: Duration) :
    Expression<LocalDateTime>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        when (currentDialect) {
            is SQLServerDialect -> {
                queryBuilder.run {
                    // SQL Server uses millisecond precision in datetime comparisons
                    // Adding a small buffer (1ms less) to ensure time comparison behaves as expected
                    val precisionAdjustedMillis = duration.toMillis() - 1

                    append("(DATEADD(millisecond, ")
                    append(precisionAdjustedMillis.toString())
                    append(", ")
                    expression.toQueryBuilder(queryBuilder)
                    append("))")
                }
            }
            else -> {
                queryBuilder.run {
                    append("(")
                    expression.toQueryBuilder(queryBuilder)
                    append(" + INTERVAL '${duration.toMillis() / 1000.0}' SECOND")
                    append(")")
                }
            }
        }
    }
}
