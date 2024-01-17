import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bean.PicBean
import utils.CommonPathUtils
import utils.DownUtils
import utils.RegexUtils
import utils.UnzipUtils
import java.io.*
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


@Composable
fun ADBControllerPanel() {
    val initOK = remember { mutableStateOf(false) }
    checkAdbExist(initOK)

    Box(modifier = Modifier.fillMaxSize()){
        if(!initOK.value){
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ){
                Text("正在初始化中...", style = TextStyle(color = Color.Black, fontSize = 14.sp, textAlign = TextAlign.Center))
            }
        }
        if(initOK.value){
            Content()
        }
    }


}

@Composable
fun Content(){
    val picList = remember { mutableStateListOf<PicBean>() }
    val textShow = remember { mutableStateOf("") }

    Column {
        Row(modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()) {
            Button(modifier = Modifier.weight(1f), onClick = {
                findScreenShotPath()
                picList.clear()
                listScreenShotPictures(picList)
            }) {
                Text("获取/刷新截图列表")
            }
            Spacer(modifier = Modifier.width(20.dp))
            Button(modifier = Modifier.weight(1f), onClick = {
                screenShot()
                picList.clear()
                listScreenShotPictures(picList)
            }) {
                Text("手机截图并发送到PC")
            }
        }
       //   TextField(
       //       value = textShow.value,
       //       onValueChange = { newText ->
       //           textShow.value = newText },
       //       label = {
       //           Text("输入点啥")
       //       }
       //   )
       //   Button(onClick = {
       //       download()
       //       // test(textShow)
       //   // sendCommandLine(textShow,ANDROID_HOME)
       //   }) {
       //       Text("test")
       //   }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().background(color = Color.Gray),
            contentPadding = PaddingValues(top = 20.dp),
        ) {
            println("picList.size:${picList.size}")
            items(picList.size) { index ->
                PicItem(picList[index])
            }
        }
    }
}

fun checkAdbExist(initOK: MutableState<Boolean>){
    val HOME_PATH = System.getenv("HOME")
    val downloadPath = "$HOME_PATH/Library/Android/sdk"
    if(!File(downloadPath).exists()){
        File(downloadPath).mkdirs()
    }

    val adbFile = File("$downloadPath/platform-tools","adb")
    if(!adbFile.exists()){
        // 下载adb
        println("没有adb，去下载")

        val fileUrl = "https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"
        val destinationDir = "$HOME_PATH/Library/Android/sdk"
        try {
            download(fileUrl, destinationDir)
            println("platform-tools 下载完毕")
            unzip(fileUrl,destinationDir)
            println("platform-tools 解压缩完毕")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        initOK.value = true
    }else{
        // 初始化成功
        println("已经有adb")
        initOK.value = true
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PicItem(pic: PicBean) {
    val userHome = System.getProperty("user.home")
    val appSupportDir = "$userHome/Library/Caches"
    val appName = "Android图片查看器"

    val privateDir = "$appSupportDir/$appName"
    val imageFile = File(privateDir, pic.fileName)
    val imageBitmap = loadImageBitmap(FileInputStream(imageFile))
    Box(
        modifier = Modifier.fillMaxWidth().wrapContentWidth()
            .background(Color.Gray), contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.wrapContentWidth().fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(modifier = Modifier.height(320.dp).combinedClickable(
                onDoubleClick = {
                    // 可以做一个大图浏览
                    downloadPicToDownloads(pic.fileName)
                },
                onClick = {},
                onLongClick = {}
            ), bitmap = imageBitmap, contentDescription = null)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                pic.fileName,
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(72.dp),
                style = TextStyle(color = Color.Black, fontSize = 14.sp, textAlign = TextAlign.Center)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

fun findScreenShotPath(){
    try {
        val processBuilder =
            ProcessBuilder("${CommonPathUtils.adbPath}/adb", "shell", "test", "-d", CommonPathUtils.SCREEN_SHOT_PATH_1,"&&","echo","yes","||","echo","no")

        // adb shell test -d /sdcard/A && echo yes || echo no

        val currentPath = System.getProperty("user.dir")
        processBuilder.directory(File(currentPath))
        val process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            println(line)
            if("yes".equals(line)){
                CommonPathUtils.screenShotPath = CommonPathUtils.SCREEN_SHOT_PATH_1
                break
            }else if("no".equals(line)){
                CommonPathUtils.screenShotPath = CommonPathUtils.SCREEN_SHOT_PATH_2
                break
            }
        }
        reader.close()
        val exitCode = process.waitFor()
        println("命令执行完成，退出码：$exitCode")
    } catch (e: IOException) {
        throw RuntimeException(e)
    } catch (e: InterruptedException) {
        throw RuntimeException(e)
    }
}

fun listScreenShotPictures(picList: MutableList<PicBean>) {
    try {
        val processBuilder =
            ProcessBuilder("${CommonPathUtils.adbPath}/adb", "shell", "ls", "cd", CommonPathUtils.screenShotPath, "ls", "-l")

        val currentPath = System.getProperty("user.dir")
        processBuilder.directory(File(currentPath))
        val process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            println(line)
            if (!line.isNullOrBlank()) {
                // val args = line!!.split(" ")
                // println("args.size:${args.size}")
                val picBean = makePicBean(line!!)
                picBean?.let { pic ->
                    println(pic)
                    if(pic.fileName.isNotBlank()){
                        picList.add(pic)
                        downloadPicToPc(pic.fileName)
                    }
                }
            }
        }
        sortByTimeReverse(picList)
        reader.close()
        val exitCode = process.waitFor()
        println("命令执行完成，退出码：$exitCode")
    } catch (e: IOException) {
        throw RuntimeException(e)
    } catch (e: InterruptedException) {
        throw RuntimeException(e)
    }
}

fun screenShot() {
    try {
        val time = SimpleDateFormat("YYYY_MM_DD_HH_mm_ss").format(Date())
        val fileName = "Screenshot_${time}.jpg"
        val screenShotDir = CommonPathUtils.screenShotPath
        val processBuilder =
            ProcessBuilder("${CommonPathUtils.adbPath}/adb", "shell", "screencap", "-p", "${screenShotDir}${fileName}")

        val currentPath = System.getProperty("user.dir")
        processBuilder.directory(File(currentPath))
        val process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            println(line)
        }
        println("截图成功 filePath = ${screenShotDir}${fileName}")
        pullToPC("${screenShotDir}${fileName}", fileName)
        reader.close()
        val exitCode = process.waitFor()
        println("命令执行完成，退出码：$exitCode")
    } catch (e: IOException) {
        throw RuntimeException(e)
    } catch (e: InterruptedException) {
        throw RuntimeException(e)
    }
}

fun pullToPC(sourcePath: String, fileName: String) {
    try {
        val downloadPath = "${System.getProperty("user.home")}/Downloads"
        val processBuilder =
            ProcessBuilder("${CommonPathUtils.adbPath}/adb", "pull", sourcePath, "${downloadPath}/${fileName}")

        val currentPath = System.getProperty("user.dir")
        processBuilder.directory(File(currentPath))
        val process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            println(line)
        }
        println("发送成功 filePath = ${downloadPath}${fileName}")
        reader.close()
        val exitCode = process.waitFor()
        println("命令执行完成，退出码：$exitCode")
    } catch (e: IOException) {
        throw RuntimeException(e)
    } catch (e: InterruptedException) {
        throw RuntimeException(e)
    }
}


fun makePicBean(logLine: String): PicBean? {
    val segments = logLine.split(" ")
    var date = ""
    var time = ""
    var fileName = ""
    segments.forEach { segment ->
        when {
            RegexUtils.isDate(segment) -> date = segment
            RegexUtils.isTime(segment) -> time = segment
            RegexUtils.isFileName(segment) -> fileName = segment
            else -> {}
        }
    }
    if (date.isBlank() && time.isBlank() && fileName.isBlank()) {
        return null
    } else {
        return PicBean(time = "$date $time", fileName = fileName)
    }
}


fun sortByTimeReverse(picList: MutableList<PicBean>) {
    try {
        picList.sortWith { pic1, pic2 ->
            val time1 = LocalDateTime.parse(pic1.time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val time2 = LocalDateTime.parse(pic2.time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            time2.compareTo(time1)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun downloadPicToDownloads(path: String) {
    try {
        // 把缓存中的图片 copy 到Downloads目录下
        val downloadPath = "${System.getProperty("user.home")}/Downloads"

        val userHome = System.getProperty("user.home")
        val appSupportDir = "$userHome/Library/Caches"
        val appName = "Android图片查看器"

        val privateDir = "$appSupportDir/$appName"
        val imageFolder = File(privateDir)
        val imageFile = File(imageFolder, path)

        imageFile.copyTo(File("$downloadPath/${path}"), true)
    } catch (e: IOException) {
        throw RuntimeException(e)
    } catch (e: InterruptedException) {
        throw RuntimeException(e)
    }
}

fun downloadPicToPc(path: String) {
    try {
        val currentPath = System.getProperty("user.dir")

        val userHome = System.getProperty("user.home")
        val appSupportDir = "$userHome/Library/Caches"
        val appName = "Android图片查看器"

        val privateDir = "$appSupportDir/$appName"
        val dir = File(privateDir)
        if (!dir.exists()) {
            dir.mkdir()
        }

        val cacheFile = File("$privateDir/${path}")
        if (cacheFile.exists()) {
            return
        }

        val processBuilder =
            ProcessBuilder("${CommonPathUtils.adbPath}/adb", "pull", "${CommonPathUtils.screenShotPath}${path}", "$privateDir/${path}")
        processBuilder.directory(File(currentPath))
        val process = processBuilder.start()

        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            println(line)
        }
        reader.close()
        val exitCode = process.waitFor()
        println("命令执行完成，退出码：$exitCode")
    } catch (e: IOException) {
        throw RuntimeException(e)
    } catch (e: InterruptedException) {
        throw RuntimeException(e)
    }
}

fun download(fileUrl: String, destinationDir: String){
    DownUtils.downloadFile(fileUrl, destinationDir)
}

fun unzip(fileUrl: String, destinationDir: String){
    val fileName = fileUrl.substringAfterLast("/")
    val zipFilePath = "$destinationDir/$fileName"
    UnzipUtils.runUnzipCommand(zipFilePath, destinationDir)
}

fun tryCatch(action: () -> Unit) {
    try {
        action.invoke()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}