package net.serverpeon.twitcharchiver.network

import com.google.common.base.Joiner
import com.google.common.collect.ImmutableList
import java.nio.file.Files
import java.util.concurrent.TimeUnit.SECONDS

val prefix = arrayOf("F:\\Downloads\\ffmpeg-20160822-61fac0e-win64-static\\bin\\ffmpeg.exe", "-i")
val suffix = arrayOf("-bsf:a", "aac_adtstoasc", "-y", "-nostdin", "-c", "copy", "out.mp4")

fun main(args: Array<String>) {
    val tempDir = Files.createTempDirectory("pbtest").toFile()
    tempDir.deleteOnExit()

    for (segments in 2000..10000 step 100) {
        println("Testing $segments segments")
        val process = generateProcessBuilder(segments).apply {
            directory(tempDir)
        }.start()

        check(!process.waitFor(1, SECONDS))
        process.destroyForcibly()
    }
}

fun generateProcessBuilder(segments: Int): ProcessBuilder {
    val input = ImmutableList.builder<String>().addAll(prefix.iterator())
            .add("concat:" + Joiner.on("|").join((0..segments).map { "part$it.ts" }))
            .addAll(suffix.iterator())
            .build()
    return ProcessBuilder(input)
}
