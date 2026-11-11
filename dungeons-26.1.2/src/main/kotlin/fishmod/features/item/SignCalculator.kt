package fishmod.features.item

import fishmod.mixin.accessors.AbstractSignEditScreenAccessor
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import java.math.BigDecimal
import java.math.RoundingMode

// Math in Hypixel input signs: "9*9" previews "= 81" and is sent as 81
object SignCalculator {

    @JvmStatic
    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, w, _ ->
            if (screen !is AbstractSignEditScreen) return@AfterInit
            ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
                if (!FishSettings.signCalculator) return@AfterExtract
                val msgs = messages(screen) ?: return@AfterExtract
                if (!isInputSign(msgs)) return@AfterExtract
                val v = evaluate(msgs[0]) ?: return@AfterExtract
                // Right of the sign's first (input) line
                ctx.text(Minecraft.getInstance().font, "§a= §f${format(v)}", w / 2 + 52, 72, -1, true)
            })
        })
    }

    // Called from removed() before vanilla sends the sign packet
    @JvmStatic
    fun beforeSend(screen: AbstractSignEditScreen) {
        if (!FishSettings.signCalculator) return
        val msgs = messages(screen) ?: return
        if (!isInputSign(msgs)) return
        val v = evaluate(msgs[0]) ?: return
        msgs[0] = format(v)
    }

    private fun messages(screen: AbstractSignEditScreen): Array<String>? =
        (screen as? AbstractSignEditScreenAccessor)?.`fishmod$getMessages`()?.takeIf { it.isNotEmpty() }

    // Hypixel input signs: typed text on line 1, "^^^^" under it
    private fun isInputSign(msgs: Array<String>): Boolean =
        SkyblockLocation.inSkyblock() || (msgs.size > 1 && msgs[1].contains("^^^"))

    // Null unless the text is a valid expression with at least one operator or bracket
    @JvmStatic
    fun evaluate(raw: String): BigDecimal? {
        val s = raw.replace(",", "").replace(" ", "").lowercase().replace('x', '*').replace('×', '*').replace('÷', '/')
        if (s.isEmpty() || s.length > 64) return null
        val p = Parser(s)
        val v = try { p.expr() } catch (_: ArithmeticException) { null } catch (_: NumberFormatException) { null } ?: return null
        if (p.pos != s.length || !p.hasOp) return null
        return v
    }

    @JvmStatic
    fun format(v: BigDecimal): String {
        val r = v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (r.signum() == 0) "0" else r.toPlainString()
    }

    private class Parser(val s: String) {
        var pos = 0
        var hasOp = false
        private var depth = 0

        fun expr(): BigDecimal? {
            var v = term() ?: return null
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]; hasOp = true
                val r = term() ?: return null
                v = if (op == '+') v.add(r) else v.subtract(r)
            }
            return v
        }

        private fun term(): BigDecimal? {
            var v = factor() ?: return null
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]; hasOp = true
                val r = factor() ?: return null
                v = if (op == '*') v.multiply(r) else {
                    if (r.signum() == 0) return null
                    v.divide(r, 10, RoundingMode.HALF_UP)
                }
                if (v.abs() > MAX) return null
            }
            return v
        }

        private fun factor(): BigDecimal? {
            if (pos >= s.length) return null
            if (s[pos] == '-') { pos++; return factor()?.negate() }
            if (s[pos] == '+') { pos++; return factor() }
            val base = if (s[pos] == '(') {
                if (++depth > 16) return null
                pos++; hasOp = true
                val v = expr() ?: return null
                if (pos >= s.length || s[pos] != ')') return null
                pos++; depth--
                v
            } else number() ?: return null
            return suffix(base)
        }

        private fun number(): BigDecimal? {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            val t = s.substring(start, pos)
            if (t.isEmpty() || t == "." || t.count { it == '.' } > 1) return null
            return BigDecimal(t)
        }

        private fun suffix(v: BigDecimal): BigDecimal {
            if (pos >= s.length) return v
            val m = when (s[pos]) { 'k' -> 1_000L; 'm' -> 1_000_000L; 'b' -> 1_000_000_000L; else -> return v }
            pos++
            return v.multiply(BigDecimal.valueOf(m))
        }
    }

    private val MAX = BigDecimal("1e18")
}
