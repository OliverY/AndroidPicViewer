package utils

object CommonPathUtils {

    val adbPath: String
        get() {
            val HOME_PATH = System.getenv("HOME")
            val adbPath = "$HOME_PATH/Library/Android/sdk/platform-tools"
            return adbPath
        }

    val SCREEN_SHOT_PATH_1 = "/sdcard/Pictures/Screenshots/"
    val SCREEN_SHOT_PATH_2 = "/sdcard/DCIM/Screenshots/"

    var screenShotPath: String = SCREEN_SHOT_PATH_1
        set(value){
            field = value
        }
        get() {
            return field
        }

}