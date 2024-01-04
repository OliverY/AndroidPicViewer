package utils

object RegexUtils {

    fun isDate(string: String): Boolean {
        return isRegexPattern(string, "^\\d{4}-(0[1-9]|1[0-2])-([0-2][0-9]|3[01])$")
    }

    fun isTime(string: String): Boolean {
        return isRegexPattern(string, "^(0[0-9]|1[0-9]|2[0-3]):([0-5][0-9])$")
    }

    fun isFileName(string: String): Boolean {
        return isRegexPattern(string, "^.*.(jpg|jpeg|png|gif)$")
    }

    private fun isRegexPattern(string: String, regex: String): Boolean {
        return regex.toRegex().matches(string)
    }

}