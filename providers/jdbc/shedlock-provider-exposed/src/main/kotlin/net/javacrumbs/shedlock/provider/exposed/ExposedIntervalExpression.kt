package net.javacrumbs.shedlock.provider.exposed

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder
import java.time.Duration
import java.time.LocalDateTime

internal fun Expression<LocalDateTime>.plus(duration: Duration): Expression<LocalDateTime> =
        IntervalExpression(this, duration.seconds)

private class IntervalExpression(
        private val expression: Expression<LocalDateTime>,
        private val seconds: Long
) : Expression<LocalDateTime>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.let {
            it.append("(")
            expression.toQueryBuilder(queryBuilder)
            it.append(" + INTERVAL '${seconds}' SECOND")
            it.append(")")
        }
    }
}
