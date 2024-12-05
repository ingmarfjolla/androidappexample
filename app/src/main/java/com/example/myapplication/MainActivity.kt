package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import android.opengl.GLES20
import android.util.Log
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logOpenGLESInfo()

        val btnFetchThreads: Button = findViewById(R.id.btn_fetch_threads)
        val tvThreadInfo: TextView = findViewById(R.id.tv_thread_info)
        val btnInteractPipe: Button = findViewById(R.id.btn_interact_pipe)
        btnFetchThreads.setOnClickListener {
            val threadInfo = getThreadInfo()
            tvThreadInfo.text = threadInfo
        }

        btnInteractPipe.setOnClickListener {
            val pipeInfo = interactWithGoldfishPipeAsync()
            //tvThreadInfo.text = pipeInfo
        }
    }

    private fun getThreadInfo(): String {
        val sb = StringBuilder()
        try {
            val taskDir = File("/proc/self/task/")
            val threadIds = taskDir.list() ?: return "No threads found."

            for (tid in threadIds) {
                val commPath = "/proc/self/task/$tid/comm"
                val commFile = File(commPath)
                val threadName = commFile.readText().trim()
                sb.append("=== /proc/self/task Information ===\n")
                sb.append("Thread ID: $tid\n")
                sb.append("Thread Name: $threadName\n")

                // Read thread status
                val statusPath = "/proc/self/task/$tid/status"
                val statusFile = File(statusPath)
                if (statusFile.exists()) {
                    val status = statusFile.readText()
                    sb.append("Thread Status:\n$status\n")
                }

                sb.append("\n")

                sb.append("=== file descriptor for:  $tid ===\n")
                sb.append(inspectFileDescriptors(tid))
                sb.append("\n")



                val stackTracePath = "/proc/self/task/$tid/stack"
                val stackTraceFile = File(stackTracePath)
                if (stackTraceFile.exists()) {
                    try {
                        val stackTrace = stackTraceFile.readText()
                        sb.append("Stack trace:\n$stackTrace\n")
                    } catch (e: Exception) {
                        sb.append("Unable to read stack trace for thread $tid: ${e.message}\n")
                    }
                }
                sb.append("\n")
                sb.append("=== /proc/self/task Information ===\n")

                sb.append("=== Java Thread Information ===\n")
                for (thread in Thread.getAllStackTraces().keys) {
                    sb.append("Thread Name: ${thread.name}\n")
                    sb.append("Thread ID: ${thread.id}\n")
                    sb.append("Thread State: ${thread.state}\n")
                    sb.append("Stack Trace:\n")
                    thread.stackTrace.forEach { stackElement ->
                        sb.append("\t$stackElement\n")
                    }
                    sb.append("\n")
                }
                sb.append("=== Java Thread Information ===\n")
            }
        } catch (e: Exception) {
            sb.append("Error: ${e.message}")
        }
        return sb.toString()
    }

    private fun inspectFileDescriptors(tid: String): String {
        val sb = StringBuilder()
        val fdDir = File("/proc/self/task/$tid/fd/")
        if (!fdDir.exists() || !fdDir.isDirectory) {
            return "FD directory not accessible for thread $tid\n"
        }

        sb.append("=== File Descriptors for Thread $tid ===\n")

        fdDir.listFiles()?.forEach { fd ->
            try {
                val target = fd.canonicalPath
                sb.append("FD: ${fd.name} -> $target\n")

//                val targetFile = File(target)
//                if (targetFile.exists() && targetFile.canRead() && targetFile.length() < 1024 * 1024) {
//                    // Limit file size to 1MB
//                    val data = targetFile.bufferedReader().useLines { lines ->
//                        lines.take(50).joinToString("\n") // Limit to 50 lines
//                    }
//                    sb.append("Data from $target (truncated):\n$data\n")
//                } else {
//                    sb.append("Skipping large or unreadable file: $target\n")
//                }
            } catch (e: Exception) {
                sb.append("Error accessing FD ${fd.name}: ${e.message}\n")
            }
        }

        return sb.toString()
    }


    fun logOpenGLESInfo() {
        val glVersion = GLES20.glGetString(GLES20.GL_VERSION)
        Log.d("OpenGLES", "GL_VERSION: $glVersion")

        val glRenderer = GLES20.glGetString(GLES20.GL_RENDERER)
        Log.d("OpenGLES", "GL_RENDERER: $glRenderer")

        val glExtensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
        Log.d("OpenGLES", "GL_EXTENSIONS: $glExtensions")
    }
    private fun intToBytes(i: Int): ByteArray =
        ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array()
    private fun interactWithGoldfishPipeAsync() {
        Thread {
            val result = interactWithGoldfishPipe()

            // Update the UI on the main thread
            runOnUiThread {
                val tvThreadInfo: TextView = findViewById(R.id.tv_thread_info)
                tvThreadInfo.text = result
            }
        }.start()
    }
    private fun interactWithGoldfishPipe(): String {
        val pipePath = "/dev/goldfish_pipe"
        val serviceName = "pipe:GLProcessPipe" + '\u0000' // Append a null character


        val sb = StringBuilder()

        val pipeFile = File(pipePath)
        if (!pipeFile.exists()) {
            return "Pipe $pipePath does not exist on this device.\n"
        }

        try {
            val pipe = RandomAccessFile(pipePath, "rw")
            Log.d("GoldfishPipe", "Opened $pipePath successfully.")
            sb.append("Opened $pipePath successfully.\n")

            // Write the service name
            pipe.writeBytes(serviceName)
            sb.append("Wrote service name: $serviceName\n")

            Log.d("GoldfishPipe", "Wrote service name: $serviceName")
//            val cmdPoll = 3
//            pipe.write(intToBytes(cmdPoll))
//            val payload = "list" + '\u0000'
//            pipe.write(payload.toByteArray())
            val confirmInt = 100
            //pipe.write(intToBytes(confirmInt))
            val OP_rcGetRendererVersion = 10000
            val OP_rcGetEGLVersion = 10001
            val OP_rcMakeCurrent = 10017
            val OP_rcSelectChecksumHelper = 10028
            val newProtocol = 1 // Example value
            val reserved = 0 // Reserved field
            val sizeWithoutChecksum = 8 + 4 + 4 // Opcode + size + 2 fields
            val checksumSize = 0 // Assuming no checksum for simplicity
            val totalSize = sizeWithoutChecksum + checksumSize

            // Construct the request buffer
            val requestBuffer = ByteBuffer.allocate(totalSize)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(OP_rcSelectChecksumHelper) // Opcode
                .putInt(totalSize) // Total size
                .putInt(newProtocol) // newProtocol
                .putInt(reserved) // reserved
            //pipe.write(intToBytes(OP_rcGetRendererVersion))
            pipe.write(requestBuffer.array())
            Log.d("GoldfishPipe", "Request (hex): ${requestBuffer.array().joinToString(" ") { it.toString(16) }}")
            sb.append("Sent rcSelectChecksumHelper command with protocol $newProtocol.\n")

            val buffer = ByteArray(2056)
            val bytesRead = pipe.read(buffer)
            val rawData = buffer.sliceArray(0 until bytesRead)
            Log.d("GoldfishPipe", "Raw response (hex): ${rawData.joinToString(" ") { it.toString(16) }}")
            if (bytesRead > 0) {
                val response = buffer.decodeToString(0, bytesRead)
                sb.append("Response: $response\n")
                Log.d("GoldfishPipe", "Read response: $response")
                val responseInt = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).int
                sb.append("Interpreted response as int: $responseInt\n")
                val response2 = ByteBuffer.wrap(buffer)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int // Assuming response is a 4-byte value
                sb.append("Response: $response2\n")
            } else {
                sb.append("No data received from pipe.\n")
                Log.d("GoldfishPipe", "No data received from pipe.")
            }

            pipe.close()
            Log.d("GoldfishPipe", "Closed the pipe.")
        } catch (e: Exception) {
            sb.append("Error interacting with $pipePath: ${e.message}\n")
            Log.e("GoldfishPipe", "Error interacting with $pipePath: ${e.message}")
        }

        return sb.toString()
    }

//    private fun interactWithGoldfishPipe(): String {
//        val pipePath = "/dev/goldfish_pipe"
//        val serviceName = "pipe:opengles" // Try valid service names like "qemud" or "opengles"
//        val sb = StringBuilder()
//
//        val pipeFile = File(pipePath)
//        if (!pipeFile.exists()) {
//            return "Pipe $pipePath does not exist on this device.\n"
//        }
//
//        try {
//            val pipe = RandomAccessFile(pipePath, "rw")
//            sb.append("Opened $pipePath successfully.\n")
//
//            // Write the service name to the pipe
//            pipe.writeBytes("$serviceName\u0000") // Null-terminated string
//            sb.append("Wrote service name: $serviceName\n")
//
//            // Read the response
//            val buffer = ByteArray(256)
//            val bytesRead = pipe.read(buffer)
//            if (bytesRead > 0) {
//                val response = buffer.decodeToString(0, bytesRead)
//                sb.append("Response from pipe: $response\n")
//            } else {
//                sb.append("No response from pipe.\n")
//            }
//
//            pipe.close()
//        } catch (e: Exception) {
//            sb.append("Error interacting with $pipePath: ${e.message}\n")
//        }
//
//        return sb.toString()
//    }

//    private fun interactWithGoldfishPipe(): String {
//        val pipePath = "/dev/goldfish_pipe"
//        val serviceName = "opengles" // You can change this to another service like "qemud"
//
//        val sb = StringBuilder()
//        val pipeFile = File(pipePath)
//
//        if (!pipeFile.exists()) {
//            return "Pipe $pipePath does not exist on this device.\n"
//        }
//
//        try {
//            val pipeOutput = FileOutputStream("/dev/goldfish_pipe")
//            pipeOutput.write("$serviceName\u0000".toByteArray()) // Null-terminated string
//            pipeOutput.close()
//            sb.append("Wrote service name: $serviceName\n")
//        } catch (e: Exception) {
//            sb.append("Error writing to /dev/goldfish_pipe: ${e.message}\n")
//        }
//
//        return sb.toString()
//    }
//





//    // Extension function to resolve symlinks
//    private fun File.readlink(): String {
//        return Runtime.getRuntime().exec(arrayOf("readlink", this.absolutePath))
//            .inputStream.bufferedReader().readText().trim()
//    }

}
