package adblib

import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.nio.ByteBuffer
import java.nio.ByteOrder


/**
 * This class provides useful functions and fields for ADB protocol details.
 * @author Cameron Gutman
 */
object AdbProtocol {
    /** The length of the ADB message header  */
    const val ADB_HEADER_LENGTH: Int = 24

    const val CMD_SYNC: Int = 0x434e5953

    /** CNXN is the connect message. No messages (except AUTH)
     * are valid before this message is received.  */
    const val CMD_CNXN: Int = 0x4e584e43

    /** The current version of the ADB protocol  */
    const val CONNECT_VERSION: Int = 0x01000000

    /** The maximum data payload supported by the ADB implementation  */
    const val CONNECT_MAXDATA: Int = 4096

    /** The payload sent with the connect message  */
    lateinit var CONNECT_PAYLOAD: ByteArray

    init {
        try {
            CONNECT_PAYLOAD = "host::\u0000".toByteArray(charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
        }
    }

    /** AUTH is the authentication message. It is part of the
     * RSA public key authentication added in Android 4.2.2.  */
    const val CMD_AUTH: Int = 0x48545541

    /** This authentication type represents a SHA1 hash to sign  */
    const val AUTH_TYPE_TOKEN: Int = 1

    /** This authentication type represents the signed SHA1 hash  */
    const val AUTH_TYPE_SIGNATURE: Int = 2

    /** This authentication type represents a RSA public key  */
    const val AUTH_TYPE_RSA_PUBLIC: Int = 3

    /** OPEN is the open stream message. It is sent to open
     * a new stream on the target device.  */
    const val CMD_OPEN: Int = 0x4e45504f

    /** OKAY is a success message. It is sent when a write is
     * processed successfully.  */
    const val CMD_OKAY: Int = 0x59414b4f

    /** CLSE is the close stream message. It it sent to close an
     * existing stream on the target device.  */
    const val CMD_CLSE: Int = 0x45534c43

    /** WRTE is the write stream message. It is sent with a payload
     * that is the data to write to the stream.  */
    const val CMD_WRTE: Int = 0x45545257

    /**
     * This function performs a checksum on the ADB payload data.
     * @param payload Payload to checksum
     * @return The checksum of the payload
     */
    private fun getPayloadChecksum(payload: ByteArray): Int {
        var checksum = 0

        for (b in payload) {
            /* We have to manually "unsign" these bytes because Java sucks */
            checksum += if (b >= 0) b.toInt()
            else b + 256
        }

        return checksum
    }

    /**
     * This function validate the ADB message by checking
     * its command, magic, and payload checksum.
     * @param msg ADB message to validate
     * @return True if the message was valid, false otherwise
     */
    fun validateMessage(msg: AdbMessage): Boolean {
        /* Magic is cmd ^ 0xFFFFFFFF */
        if (msg.command != (msg.magic xor -0x1)) return false

        if (msg.payloadLength != 0) {
            if (getPayloadChecksum(msg.payload) != msg.checksum) return false
        }

        return true
    }

    /**
     * This function generates an ADB message given the fields.
     * @param cmd Command identifier
     * @param arg0 First argument
     * @param arg1 Second argument
     * @param payload Data payload
     * @return Byte array containing the message
     */
    fun generateMessage(cmd: Int, arg0: Int, arg1: Int, payload: ByteArray?): ByteArray {
        /* struct message {
         * 		unsigned command;       // command identifier constant
         * 		unsigned arg0;          // first argument
         * 		unsigned arg1;          // second argument
         * 		unsigned data_length;   // length of payload (0 is allowed)
         * 		unsigned data_check;    // checksum of data payload
         * 		unsigned magic;         // command ^ 0xffffffff
         * };
         */
        val message = if (payload != null) {
            ByteBuffer.allocate(ADB_HEADER_LENGTH + payload.size)
                .order(ByteOrder.LITTLE_ENDIAN)
        } else {
            ByteBuffer.allocate(ADB_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)
        }

        message.putInt(cmd)
        message.putInt(arg0)
        message.putInt(arg1)

        if (payload != null) {
            message.putInt(payload.size)
            message.putInt(getPayloadChecksum(payload))
        } else {
            message.putInt(0)
            message.putInt(0)
        }

        message.putInt(cmd xor -0x1)

        if (payload != null) {
            message.put(payload)
        }

        return message.array()
    }

    /**
     * Generates a connect message with default parameters.
     * @return Byte array containing the message
     */
    fun generateConnect(): ByteArray {
        return generateMessage(CMD_CNXN, CONNECT_VERSION, CONNECT_MAXDATA, CONNECT_PAYLOAD)
    }

    /**
     * Generates an auth message with the specified type and payload.
     * @param type Authentication type (see AUTH_TYPE_* constants)
     * @param data The payload for the message
     * @return Byte array containing the message
     */
    fun generateAuth(type: Int, data: ByteArray?): ByteArray {
        return generateMessage(CMD_AUTH, type, 0, data)
    }

    /**
     * Generates an open stream message with the specified local ID and destination.
     * @param localId A unique local ID identifying the stream
     * @param dest The destination of the stream on the target
     * @return Byte array containing the message
     * @throws UnsupportedEncodingException If the destination cannot be encoded to UTF-8
     */
    @Throws(UnsupportedEncodingException::class)
    fun generateOpen(localId: Int, dest: String): ByteArray {
        val bbuf = ByteBuffer.allocate(dest.length + 1)
        bbuf.put(dest.toByteArray(charset("UTF-8")))
        bbuf.put(0.toByte())
        return generateMessage(CMD_OPEN, localId, 0, bbuf.array())
    }

    /**
     * Generates a write stream message with the specified IDs and payload.
     * @param localId The unique local ID of the stream
     * @param remoteId The unique remote ID of the stream
     * @param data The data to provide as the write payload
     * @return Byte array containing the message
     */
	@JvmStatic
	fun generateWrite(localId: Int, remoteId: Int, data: ByteArray?): ByteArray {
        return generateMessage(CMD_WRTE, localId, remoteId, data)
    }

    /**
     * Generates a close stream message with the specified IDs.
     * @param localId The unique local ID of the stream
     * @param remoteId The unique remote ID of the stream
     * @return Byte array containing the message
     */
	@JvmStatic
	fun generateClose(localId: Int, remoteId: Int): ByteArray {
        return generateMessage(CMD_CLSE, localId, remoteId, null)
    }

    /**
     * Generates an okay message with the specified IDs.
     * @param localId The unique local ID of the stream
     * @param remoteId The unique remote ID of the stream
     * @return Byte array containing the message
     */
	@JvmStatic
	fun generateReady(localId: Int, remoteId: Int): ByteArray {
        return generateMessage(CMD_OKAY, localId, remoteId, null)
    }

    /**
     * This class provides an abstraction for the ADB message format.
     * @author Cameron Gutman
     */
    class AdbMessage {
        /** The command field of the message  */
        var command: Int = 0

        /** The arg0 field of the message  */
        var arg0: Int = 0

        /** The arg1 field of the message  */
        var arg1: Int = 0

        /** The payload length field of the message  */
        var payloadLength: Int = 0

        /** The checksum field of the message  */
        var checksum: Int = 0

        /** The magic field of the message  */
        var magic: Int = 0

        /** The payload of the message  */
        lateinit var payload: ByteArray

        companion object {
            /**
             * Read and parse an ADB message from the supplied input stream.
             * This message is NOT validated.
             * @param in InputStream object to read data from
             * @return An AdbMessage object represented the message read
             * @throws IOException If the stream fails while reading
             */
            @Throws(IOException::class)
            fun parseAdbMessage(`in`: InputStream): AdbMessage {
                val msg = AdbMessage()
                val packet = ByteBuffer.allocate(ADB_HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)


                /* Read the header first */
                var dataRead = 0
                do {
                    val bytesRead = `in`.read(packet.array(), dataRead, 24 - dataRead)

                    if (bytesRead < 0) throw IOException("Stream closed")
                    else dataRead += bytesRead
                } while (dataRead < ADB_HEADER_LENGTH)


                /* Pull out header fields */
                msg.command = packet.getInt()
                msg.arg0 = packet.getInt()
                msg.arg1 = packet.getInt()
                msg.payloadLength = packet.getInt()
                msg.checksum = packet.getInt()
                msg.magic = packet.getInt()


                /* If there's a payload supplied, read that too */
                if (msg.payloadLength != 0) {
                    msg.payload = ByteArray(msg.payloadLength)

                    dataRead = 0
                    do {
                        val bytesRead = `in`.read(msg.payload, dataRead, msg.payloadLength - dataRead)

                        if (bytesRead < 0) throw IOException("Stream closed")
                        else dataRead += bytesRead
                    } while (dataRead < msg.payloadLength)
                }

                return msg
            }
        }
    }
}
