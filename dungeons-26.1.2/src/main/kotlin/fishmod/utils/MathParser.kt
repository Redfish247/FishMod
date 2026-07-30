package fishmod.utils

import java.util.Stack
import java.util.regex.Pattern

/**
 * This is purely made for the searchbar.
 * The parser is based on Shunting Yard and Reverse Polish Notation.
 * Yes this is commented more than other files, but this was more fun,
 * so I actually felt like documenting it.
 */
object MathParser {

    private val NUMBER_PATTERN: Pattern = Pattern.compile("^(?<num>-?\\d+)(?<decimal>\\.\\d+)?(?<unit>[bBmMkK])?")
    private val TOKEN_PATTERN: Pattern = Pattern.compile("[()+\\-*/^]")

    /**
     * Parses an expression and returns the value.
     * @param string The expression to parse
     * @return The value if it's a valid expression, otherwise NaN
     */
    @JvmStatic
    fun parseExpression(string: String?): Double {
        if (string == null) return Double.NaN

        val tokens: ArrayList<String>
        try {
            tokens = toTokens(string)
        } catch (e: IllegalArgumentException) {
            return Double.NaN
        }

        if (tokens.isEmpty()) return Double.NaN

        if (tokens.size == 1) {
            try {
                //incase it's just a number
                return tokenToNum(tokens[0])
            } catch (ignored: NumberFormatException) {
                return Double.NaN
            }
        }

        val rpnTokens = toRPN(tokens) ?: return Double.NaN

        try {
            return parseRPN(rpnTokens)
        } catch (e: IllegalArgumentException) {
            return Double.NaN
        }
    }

    /**
     * Converts a String to a number.
     * Supports orders of magnitudes like b, m and k.
     * It also supports negative and decimal numbers.
     * If it can't convert the number,
     * `NumberFormatException` will be thrown.
     *
     * @param token The token to convert to a number
     * @return The converted number
     */
    private fun tokenToNum(token: String): Double {
        val matcher = NUMBER_PATTERN.matcher(token)
        if (!matcher.find()) {
            throw NumberFormatException()
        }

        val num = matcher.group("num")
        val decimal = matcher.group("decimal")
        val unit = matcher.group("unit")

        var value: Double = if (decimal != null) {
            (num + decimal).toDouble()
        } else {
            num.toDouble()
        }

        if (unit != null) {
            when (unit) {
                "b", "B" -> value *= 1_000_000_000
                "m", "M" -> value *= 1000_000
                "k", "K" -> value *= 1000
            }
        }

        return value
    }

    /**
     * Parses an RPN token, Will throw an `IllegalArgumentException` when handling an invalid token.
     * @param stack the stack to keep track of previously handled tokens
     * @param token The token to parse
     */
    private fun handleTokenRPN(stack: Stack<Double>, token: String) {
        try {
            val value = tokenToNum(token)
            stack.push(value)
            return
        } catch (ignored: NumberFormatException) {
        }

        if (stack.size < 2) {
            if (stack.size == 1) {
                if (token == "-") {
                    //edge case to make for example -(2+3) negative
                    stack.push(stack.pop() * -1)
                    return
                }
            }

            throw IllegalArgumentException("Invalid token")
        }

        val right = stack.pop()
        val left = stack.pop()

        when (token) {
            "+" -> stack.push(left + right)
            "-" -> stack.push(left - right)
            "*" -> stack.push(left * right)
            "/" -> stack.push(left / right)
            "^" -> stack.push(Math.pow(left, right))
            else -> throw IllegalArgumentException("Invalid operator")
        }
    }

    /**
     * Parses an expression of tokens in RPN.
     * Reference: [reverse-polish-notation-evaluator-in-javascript](https://inspirnathan.com/posts/150-reverse-polish-notation-evaluator-in-javascript/)
     * @param tokens The tokens to parse
     * @return The parsed value, `Double.NaN` if its invalid
     */
    private fun parseRPN(tokens: ArrayList<String>): Double {
        val stack = Stack<Double>()

        for (token in tokens) {
            handleTokenRPN(stack, token)
        }

        if (stack.isEmpty()) return Double.NaN
        return stack.pop()
    }

    private fun getPrecedence(token: String): Int {
        return when (token) {
            "^" -> 4
            "*", "/" -> 3
            "+", "-" -> 2
            else -> -1
        }
    }

    private fun getAssociative(token: String): String {
        return when (token) {
            "^" -> "right"
            "*", "/", "+", "-" -> "left"
            else -> ""
        }
    }

    private fun isANumber(token: String): Boolean {
        val matcher = NUMBER_PATTERN.matcher(token)
        return matcher.find()
    }

    private fun addToken(output: ArrayList<String>, stack: Stack<String>, token: String): Boolean {
        if (isANumber(token)) {
            output.add(token)
            return true
        } else if (getPrecedence(token) != -1) {
            if (stack.isEmpty()) {
                stack.push(token)
                return true
            }

            var top = stack.peek()

            while (top != "(" && (getPrecedence(top) > getPrecedence(token)
                        || (getPrecedence(top) == getPrecedence(token) && getAssociative(token) == "left"))
            ) {
                output.add(stack.pop())
                if (stack.isEmpty()) break
                top = stack.peek()
            }
            stack.push(token)
            return true
        } else if (token == "(") {
            stack.push(token)
            return true
        } else if (token == ")") {
            var top = stack.peek()
            while (top != "(") {
                if (stack.isEmpty()) return false
                output.add(stack.pop())
                if (stack.isEmpty()) return false
                top = stack.peek()
            }

            if (stack.isEmpty() || stack.peek() != "(") return false
            stack.pop()
            return true
        } else {
            return false
        }
    }

    /**
     * Converts a list of tokens to a new in RPN
     * Refrence: [151-shunting-yard-algorithm-in-javascript](https://inspirnathan.com/posts/151-shunting-yard-algorithm-in-javascript/)
     * @param tokens The tokens to convert
     * @return The list of tokens in RPN
     */
    private fun toRPN(tokens: ArrayList<String>): ArrayList<String>? {
        val output = ArrayList<String>()
        val stack = Stack<String>()

        for (token in tokens) {
            if (!addToken(output, stack, token)) {
                return null
            }
        }

        while (!stack.isEmpty()) {
            val token = stack.pop()
            if (token == "(") return null
            output.add(token)
        }

        return output
    }

    /**
     * Split an expression string to an ArrayList of tokens.
     * Throws an `IllegalArgumentException` when an invalid token is read.
     *
     * @param string The string to split into token
     * @return The list of tokens
     */
    private fun toTokens(string: String): ArrayList<String> {
        val output = ArrayList<String>()
        var copiedString = string.replace(" ", "")

        //to handle binary subtraction not getting confused
        //with unary subtraction
        var wasPrevNum = false

        while (copiedString.isNotEmpty()) {
            var matcher = NUMBER_PATTERN.matcher(copiedString)
            if (!wasPrevNum && matcher.find()) {
                val token = matcher.group()
                val index = copiedString.indexOf(token)
                if (index == 0) {
                    copiedString = copiedString.substring(token.length)
                    output.add(token)
                    wasPrevNum = true
                    continue
                }
            }

            matcher = TOKEN_PATTERN.matcher(copiedString)
            if (matcher.find()) {
                val token = matcher.group()
                val index = copiedString.indexOf(token)
                if (index == 0) {
                    copiedString = copiedString.substring(token.length)
                    output.add(token)
                    wasPrevNum = false
                    continue
                }
            }

            throw IllegalArgumentException("String: $string has invalid token, ended at: $copiedString")
        }

        return output
    }
}
