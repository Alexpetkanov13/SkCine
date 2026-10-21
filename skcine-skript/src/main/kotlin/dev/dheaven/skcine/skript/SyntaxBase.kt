package dev.dheaven.skcine.skript

import ch.njol.skript.lang.Condition
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser.ParseResult
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.util.Kleenean
import org.bukkit.event.Event
import org.skriptlang.skript.registration.DefaultSyntaxInfos
import org.skriptlang.skript.registration.SyntaxInfo
import org.skriptlang.skript.registration.SyntaxRegistry

@Suppress("UNCHECKED_CAST")
fun <T> Array<out Expression<*>>.expr(i: Int): Expression<T> = this[i] as Expression<T>

@Suppress("UNCHECKED_CAST")
fun <T> Array<out Expression<*>>.opt(i: Int): Expression<T>? = this.getOrNull(i) as Expression<T>?

/** Effect base that records the script location for diagnostics. */
abstract class CineEffect : Effect() {
    protected var where: String? = null
    protected var pattern = 0

    final override fun init(expressions: Array<out Expression<*>>, matchedPattern: Int, isDelayed: Kleenean, parseResult: ParseResult): Boolean {
        where = Ctx.here()
        pattern = matchedPattern
        return setup(expressions, matchedPattern, parseResult)
    }

    protected abstract fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean

    protected abstract val description: String

    override fun toString(event: Event?, debug: Boolean) = description
}

abstract class CineCondition : Condition() {
    protected var where: String? = null

    final override fun init(expressions: Array<out Expression<*>>, matchedPattern: Int, isDelayed: Kleenean, parseResult: ParseResult): Boolean {
        where = Ctx.here()
        return setup(expressions, matchedPattern, parseResult)
    }

    protected abstract fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean

    protected abstract val description: String

    override fun toString(event: Event?, debug: Boolean) = description
}

/** Expression base. Subclasses return a typed array via [values]. */
abstract class CineExpression<T : Any>(private val type: Class<T>, private val single: Boolean = true) : SimpleExpression<T>() {
    protected var where: String? = null
    protected var pattern = 0

    final override fun init(expressions: Array<out Expression<*>>, matchedPattern: Int, isDelayed: Kleenean, parseResult: ParseResult): Boolean {
        where = Ctx.here()
        pattern = matchedPattern
        return setup(expressions, matchedPattern, parseResult)
    }

    protected abstract fun setup(e: Array<out Expression<*>>, pattern: Int, result: ParseResult): Boolean

    protected abstract fun values(event: Event): List<T>

    @Suppress("UNCHECKED_CAST")
    override fun get(event: Event): Array<T> {
        val list = values(event)
        val arr =
            java.lang.reflect.Array
                .newInstance(type, list.size) as Array<T>
        list.forEachIndexed { i, v -> arr[i] = v }
        return arr
    }

    override fun isSingle() = single

    override fun getReturnType(): Class<out T> = type

    protected abstract val description: String

    override fun toString(event: Event?, debug: Boolean) = description
}

fun <E : Effect> SyntaxRegistry.effect(cls: Class<E>, supplier: () -> E, vararg patterns: String) = register(
    SyntaxRegistry.EFFECT,
    SyntaxInfo
        .builder(cls)
        .supplier(supplier)
        .addPatterns(*patterns)
        .build(),
)

fun <E : Condition> SyntaxRegistry.condition(cls: Class<E>, supplier: () -> E, vararg patterns: String) = register(
    SyntaxRegistry.CONDITION,
    SyntaxInfo
        .builder(cls)
        .supplier(supplier)
        .addPatterns(*patterns)
        .build(),
)

fun <E : Expression<R>, R> SyntaxRegistry.expression(cls: Class<E>, returnType: Class<R>, supplier: () -> E, vararg patterns: String) = register(
    SyntaxRegistry.EXPRESSION,
    DefaultSyntaxInfos.Expression
        .builder(cls, returnType)
        .supplier(supplier)
        .addPatterns(*patterns)
        .build(),
)
