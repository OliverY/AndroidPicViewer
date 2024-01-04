package adblib

import adblib.AdbProtocol.generateClose
import adblib.AdbProtocol.generateReady
import adblib.AdbProtocol.generateWrite
import java.io.Closeable
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class abstracts the underlying ADB streams
 * @author Cameron Gutman
 */
class AdbStream(
    /** The AdbConnection object that the stream communicates over  */
    private val adbConn: AdbConnection,
    /** The local ID of the stream  */
    private val localId: Int
) : Closeable {
    /** The remote ID of the stream  */
    private var remoteId = 0

    /** Indicates whether a write is currently allowed  */
    private val writeReady = AtomicBoolean(false)

    /** A queue of data from the target's write packets  */
    private val readQueue: Queue<ByteArray> = ConcurrentLinkedQueue()

    /**
     * Retreives whether the stream is closed or not
     * @return True if the stream is close, false if not
     */
    /** Indicates whether the connection is closed already  */
    var isClosed: Boolean = false
        private set

    /**
     * Called by the connection thread to indicate newly received data.
     * @param payload Data inside the write message
     */
    fun addPayload(payload: ByteArray) {
        synchronized(readQueue) {
            readQueue.add(payload)
            (readQueue as Object).notifyAll()
        }
    }

    /**
     * Called by the connection thread to send an OKAY packet, allowing the
     * other side to continue transmission.
     * @throws IOException If the connection fails while sending the packet
     */
    @Throws(IOException::class)
    fun sendReady() {
        /* Generate and send a READY packet */
        val packet = generateReady(localId, remoteId)
        adbConn.outputStream!!.write(packet)
        adbConn.outputStream!!.flush()
    }

    /**
     * Called by the connection thread to update the remote ID for this stream
     * @param remoteId New remote ID
     */
    fun updateRemoteId(remoteId: Int) {
        this.remoteId = remoteId
    }

    /**
     * Called by the connection thread to indicate the stream is okay to send data.
     */
    fun readyForWrite() {
        writeReady.set(true)
    }

    /**
     * Called by the connection thread to notify that the stream was closed by the peer.
     */
    fun notifyClose() {
        /* We don't call close() because it sends another CLOSE */
        isClosed = true


        /* Unwait readers and writers */
        synchronized(this) {
            (this as Object).notifyAll()
        }
        synchronized(readQueue) {
            (readQueue as Object).notifyAll()
        }
    }

    /**
     * Reads a pending write payload from the other side.
     * @return Byte array containing the payload of the write
     * @throws InterruptedException If we are unable to wait for data
     * @throws IOException If the stream fails while waiting
     */
    @Throws(InterruptedException::class, IOException::class)
    fun read(): ByteArray? {
        var data: ByteArray? = null

        synchronized(readQueue) {
            /* Wait for the connection to close or data to be received */
            while (!isClosed && (readQueue.poll().also { data = it }) == null) {
                (readQueue as Object).wait()
            }
            if (isClosed) {
                throw IOException("Stream closed")
            }
        }

        return data
    }

    /**
     * Sends a write packet with a given String payload.
     * @param payload Payload in the form of a String
     * @throws IOException If the stream fails while sending data
     * @throws InterruptedException If we are unable to wait to send data
     */
    @Throws(IOException::class, InterruptedException::class)
    fun write(payload: String) {
        /* ADB needs null-terminated strings */
        write(payload.toByteArray(charset("UTF-8")), false)
        write(byteArrayOf(0), true)
    }

    /**
     * Queues a write packet and optionally sends it immediately.
     * @param payload Payload in the form of a byte array
     * @param flush Specifies whether to send the packet immediately
     * @throws IOException If the stream fails while sending data
     * @throws InterruptedException If we are unable to wait to send data
     */
    /**
     * Sends a write packet with a given byte array payload.
     * @param payload Payload in the form of a byte array
     * @throws IOException If the stream fails while sending data
     * @throws InterruptedException If we are unable to wait to send data
     */
    @JvmOverloads
    @Throws(IOException::class, InterruptedException::class)
    fun write(payload: ByteArray?, flush: Boolean = true) {
        synchronized(this) {
            /* Make sure we're ready for a write */
            while (!isClosed && !writeReady.compareAndSet(true, false)) (this as Object).wait()
            if (isClosed) {
                throw IOException("Stream closed")
            }
        }


        /* Generate a WRITE packet and send it */
        val packet = generateWrite(localId, remoteId, payload)
        adbConn.outputStream!!.write(packet)

        if (flush) adbConn.outputStream!!.flush()
    }

    /**
     * Closes the stream. This sends a close message to the peer.
     * @throws IOException If the stream fails while sending the close message.
     */
    @Throws(IOException::class)
    override fun close() {
        synchronized(this) {
            /* This may already be closed by the remote host */
            if (isClosed) return


            /* Notify readers/writers that we've closed */
            notifyClose()
        }

        val packet = generateClose(localId, remoteId)
        adbConn.outputStream!!.write(packet)
        adbConn.outputStream!!.flush()
    }
}
