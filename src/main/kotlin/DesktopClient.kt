import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class DesktopClient {
    private lateinit var socket: Socket
    private lateinit var inReader: BufferedReader
    private lateinit var outWriter: PrintWriter

    fun connectToAndroidDevice(ipAddress: String, port: Int) {
        try {
            socket = Socket(ipAddress, port)
            inReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            outWriter = PrintWriter(socket.getOutputStream(), true)

            // 发送和接收数据
            outWriter.println("Hello, Android!") // 发送数据到Android设备
            val response = inReader.readLine() // 从Android设备接收数据
            println("Response from Android: $response")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun disconnectFromAndroidDevice() {
        try {
            if (::outWriter.isInitialized) {
                outWriter.close()
            }
            if (::inReader.isInitialized) {
                inReader.close()
            }
            if (::socket.isInitialized) {
                socket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}