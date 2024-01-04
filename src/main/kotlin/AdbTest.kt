import adblib.AdbBase64
import adblib.AdbConnection
import adblib.AdbCrypto
import adblib.AdbStream
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.Socket
import java.net.UnknownHostException
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.util.*

class AdbTest {
    fun start(ip: String?) {
        val `in` = Scanner(System.`in`)
        val adb: AdbConnection
        val sock: Socket
        val crypto: AdbCrypto?

        // Setup the crypto object required for the AdbConnection
        try {
            crypto = setupCrypto("pub.key", "priv.key")
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            return
        } catch (e: InvalidKeySpecException) {
            e.printStackTrace()
            return
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }

        // Connect the socket to the remote host
        println("Socket connecting...")
        try {
            sock = Socket(ip, 5555)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            return
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
        println("Socket connected")

        // Construct the AdbConnection object
        try {
            adb = AdbConnection.create(sock, crypto)
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }

        // Start the application layer connection process
        println("ADB connecting...")
        try {
            adb.connect()
        } catch (e: IOException) {
            e.printStackTrace()
            return
        } catch (e: InterruptedException) {
            e.printStackTrace()
            return
        }
        println("ADB connected")

        // Open the shell stream of ADB
        val stream: AdbStream
        try {
            stream = adb.open("shell:")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            return
        } catch (e: IOException) {
            e.printStackTrace()
            return
        } catch (e: InterruptedException) {
            e.printStackTrace()
            return
        }

        // Start the receiving thread
        Thread(Runnable {
            while (!stream.isClosed) try {
                // Print each thing we read from the shell stream
                print(String(stream.read()!!, charset("US-ASCII")))
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                return@Runnable
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return@Runnable
            } catch (e: IOException) {
                e.printStackTrace()
                return@Runnable
            }
        }).start()

        // We become the sending thread
        while (true) {
            try {
                stream.write(`in`.nextLine() + '\n')
            } catch (e: IOException) {
                e.printStackTrace()
                return
            } catch (e: InterruptedException) {
                e.printStackTrace()
                return
            }
        }
    }

    companion object {
        val base64Impl: AdbBase64 = object:AdbBase64 {
            override fun encodeToString(data: ByteArray?): String? {
                return data?.let {
                    Base64.getEncoder().encodeToString(it)
                }
            }
            // This implements the AdbBase64 interface required for AdbCrypto
            }

        // This function loads a keypair from the specified files if one exists, and if not,
        // it creates a new keypair and saves it in the specified files
        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class, IOException::class)
        private fun setupCrypto(pubKeyFile: String, privKeyFile: String): AdbCrypto? {
            val pub = File(pubKeyFile)
            val priv = File(privKeyFile)
            var c: AdbCrypto? = null

            // Try to load a key pair from the files
            if (pub.exists() && priv.exists()) {
                c = try {
                    AdbCrypto.loadAdbKeyPair(base64Impl, priv, pub)
                } catch (e: IOException) {
                    // Failed to read from file
                    null
                } catch (e: InvalidKeySpecException) {
                    // Key spec was invalid
                    null
                } catch (e: NoSuchAlgorithmException) {
                    // RSA algorithm was unsupported with the crypo packages available
                    null
                }
            }

            if (c == null) {
                // We couldn't load a key, so let's generate a new one
                c = AdbCrypto.generateAdbKeyPair(base64Impl)

                // Save it
                c.saveAdbKeyPair(priv, pub)
                println("Generated new keypair")
            } else {
                println("Loaded existing keypair")
            }

            return c
        }
    }
}