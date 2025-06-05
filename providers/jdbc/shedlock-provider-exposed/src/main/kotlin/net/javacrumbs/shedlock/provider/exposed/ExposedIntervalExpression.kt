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
                queryBuilder.let {
                    it.append("(DATEADD(millisecond, ")
                    it.append(duration.toMillis().toString())
                    it.append(", ")
                    expression.toQueryBuilder(queryBuilder)
                    it.append("))")
                }
            }
            else -> {
                queryBuilder.let {
                    it.append("(")
                    expression.toQueryBuilder(queryBuilder)
                    it.append(" + INTERVAL '${duration.seconds}' SECOND")
                    it.append(")")
                }
            }
        }
    }
}
