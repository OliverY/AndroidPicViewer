package utils

object CommonPathUtils {

    val adbPath: String
        get() {
            val HOME_PATH = System.getenv("HOME")
            val adbPath = "$HOME_PATH/Library/Android/sdk/platform-tools"
            return adbPath
        }

}