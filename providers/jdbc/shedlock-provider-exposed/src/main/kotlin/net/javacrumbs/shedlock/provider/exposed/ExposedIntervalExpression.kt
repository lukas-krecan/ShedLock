package net.javacrumbs.shedlock.provider.exposed

import java.time.Duration
import java.time.LocalDateTime
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.vendors.SQLServerDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect

internal fun Expression<LocalDateTime>.plus(duration: Duration): Expression<LocalDateTime> =
    IntervalExpression(this, duration)

private class IntervalExpression(private val expression: Expression<LocalDateTime>, private val duration: Duration) :
    Expression<LocalDateTime>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        when (currentDialect) {
            is SQLServerDialect -> {
                queryBuilder.run {
                    append("(DATEADD(millisecond, ")
                    append(duration.toMillis().toString())
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
