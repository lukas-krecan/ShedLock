package net.javacrumbs.shedlock.provider.exposed

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.vendors.SQLServerDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.time.Duration
import java.time.LocalDateTime

internal fun Expression<LocalDateTime>.plus(duration: Duration): Expression<LocalDateTime> =
        IntervalExpression(this, duration)

private class IntervalExpression(
        private val expression: Expression<LocalDateTime>,
        private val duration: Duration
) : Expression<LocalDateTime>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        when(currentDialect) {
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
                    append(" + INTERVAL '${duration.seconds}' SECOND")
                    append(")")
                }
            }
        }
    }
}
