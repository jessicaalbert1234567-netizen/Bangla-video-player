package com.example.automkvdubber.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.example.automkvdubber.model.DubberSettings
import com.example.automkvdubber.model.GeneratedOutputs
import com.example.automkvdubber.model.LogLevel
import com.example.automkvdubber.model.PipelineStep
import com.example.automkvdubber.model.SubtitleItem
import com.example.automkvdubber.util.StorageHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class DubbingPipelineManager(
    private val context: Context,
    private val translationService: GtxTranslationService = GtxTranslationService()
) {

    private val utteranceListeners = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
        FFmpegKit.cancel()
    }

    suspend fun executePipeline(
        mkvUri: Uri,
        tts: TextToSpeech,
        settings: DubberSettings,
        onStepChanged: (PipelineStep, Int, String) -> Unit,
        onLog: (String, LogLevel) -> Unit,
        onSubtitlesParsed: (List<SubtitleItem>) -> Unit
    ): GeneratedOutputs = withContext(Dispatchers.IO) {
        isCancelled = false
        val workDir = File(context.cacheDir, "dubber_work_${System.currentTimeMillis()}").apply { mkdirs() }
        val rawBaseName = StorageHelper.getFileName(context, mkvUri)
        val baseName = rawBaseName.substringBeforeLast(".").ifEmpty { "movie" }

        onLog("🎬 চলচ্চিত্র নির্বাচন: $rawBaseName (${StorageHelper.getFileSizeFormatted(context, mkvUri)})", LogLevel.INFO)

        val inputHandle = StorageHelper.obtainInputHandle(context, mkvUri, workDir)
        val safInput = inputHandle.path

        try {
            // STEP 1: PREPARING INPUT
            onStepChanged(PipelineStep.PREPARING, 5, "MKV ইনপুট যাচাই করা হচ্ছে...")
            if (safInput.isBlank()) {
                throw IllegalStateException("MKV ফাইলের জন্য সরাসরি রিড হ্যান্ডেল পাওয়া যায়নি।")
            }
            onLog("✅ ফাইল হ্যান্ডেল প্রস্তুত: $safInput", LogLevel.SUCCESS)

            if (isCancelled) return@withContext GeneratedOutputs()

            // STEP 2: EXTRACT TRACKS
            onStepChanged(PipelineStep.EXTRACTING, 15, "অরিজিনাল অডিও ও ইংলিশ সাবটাইটেল বের করা হচ্ছে...")
            val extractedAudioFile = File(workDir, "${baseName}_original_audio.aac")
            val extractedEngSrtFile = File(workDir, "${baseName}_english.srt")

            val audioExtractCmd = "-y -i \"$safInput\" -map 0:a:0 -vn -c:a aac -b:a 192k \"${extractedAudioFile.absolutePath}\""
            onLog("⏳ মূল অডিও ট্র্যাক এক্সট্রাক্ট করা হচ্ছে...", LogLevel.INFO)
            val audioSession = FFmpegKit.execute(audioExtractCmd)

            if (!ReturnCode.isSuccess(audioSession.returnCode) || !extractedAudioFile.exists()) {
                onLog("⚠️ প্রাথমিক অডিও এক্সট্রাকশন ব্যর্থ হয়েছে, কপি মোডে চেষ্টা করা হচ্ছে...", LogLevel.WARNING)
                FFmpegKit.execute("-y -i \"$safInput\" -map 0:a:0 -vn -c:a copy \"${extractedAudioFile.absolutePath}\"")
            }

            if (extractedAudioFile.exists()) {
                onLog("✅ মূল অডিও সফলভাবে সংরক্ষিত: ${extractedAudioFile.name} (${StorageHelper.formatBytes(extractedAudioFile.length())})", LogLevel.SUCCESS)
            }

            // Extract subtitle track
            onLog("⏳ ইংলিশ সাবটাইটেল ট্র্যাক বের করা হচ্ছে...", LogLevel.INFO)
            val engSubCmd = "-y -i \"$safInput\" -map 0:s:m:language:eng -c:s srt \"${extractedEngSrtFile.absolutePath}\""
            var subSession = FFmpegKit.execute(engSubCmd)

            if (!ReturnCode.isSuccess(subSession.returnCode) || !extractedEngSrtFile.exists() || extractedEngSrtFile.length() == 0L) {
                onLog("⚠️ ভাষা নির্দিষ্ট করা সাবটাইটেল মেলেনি, ১ম সাবটাইটেল ট্র্যাকে খোঁজা হচ্ছে...", LogLevel.WARNING)
                val fallbackSubCmd = "-y -i \"$safInput\" -map 0:s:0 -c:s srt \"${extractedEngSrtFile.absolutePath}\""
                subSession = FFmpegKit.execute(fallbackSubCmd)
            }

            if (!extractedEngSrtFile.exists() || extractedEngSrtFile.length() == 0L) {
                throw IllegalStateException("এই MKV ফাইলটিতে কোনো সাবটাইটেল (SRT/ASS/SUB) ট্র্যাক পাওয়া যায়নি।")
            }

            onLog("✅ সাবটাইটেল ট্র্যাক সফলভাবে নিষ্কাশিত (${StorageHelper.formatBytes(extractedEngSrtFile.length())})", LogLevel.SUCCESS)

            if (isCancelled) return@withContext GeneratedOutputs()

            // Save original audio and english srt to Downloads/FileTranslato
            val savedAudioUri = StorageHelper.saveToDownloads(context, extractedAudioFile, "${baseName}_original_audio.aac", "audio/aac")
            val savedEngSrtUri = StorageHelper.saveToDownloads(context, extractedEngSrtFile, "${baseName}_english.srt", "application/x-subrip")

            // STEP 3: PARSE AND TRANSLATE SRT
            onStepChanged(PipelineStep.TRANSLATING, 30, "সাবটাইটেল পার্স এবং বাংলায় অনুবাদ করা হচ্ছে...")
            val subtitleItems = parseSrtFile(extractedEngSrtFile)
            if (subtitleItems.isEmpty()) {
                throw IllegalStateException("সাবটাইটেল ফাইলে কোনো পাঠ্য পাওয়া যায়নি।")
            }
            onLog("📝 মোট সাবটাইটেল সংলাপ সংখ্যা: ${subtitleItems.size} টি", LogLevel.INFO)
            onSubtitlesParsed(subtitleItems)

            translationService.translateSubtitles(
                items = subtitleItems,
                sourceLang = "en",
                targetLang = settings.targetLanguageCode
            ) { current, total, statusText ->
                val progress = 30 + (current * 25 / total)
                onStepChanged(PipelineStep.TRANSLATING, progress, statusText)
            }

            onLog("✅ সম্পূর্ণ সাবটাইটেল বাংলায় অনুবাদ সম্পন্ন হয়েছে।", LogLevel.SUCCESS)

            // Save translated Bengali SRT
            val banglaSrtFile = File(workDir, "${baseName}_bangla.srt")
            saveBanglaSrtFile(subtitleItems, banglaSrtFile)
            val savedBanglaSrtUri = StorageHelper.saveToDownloads(
                context,
                banglaSrtFile,
                "${baseName}_bangla.srt",
                "application/x-subrip"
            )
            onLog("📁 বাংলা SRT সেভ হয়েছে: Download/FileTranslato/${baseName}_bangla.srt", LogLevel.SUCCESS)

            if (isCancelled) return@withContext GeneratedOutputs()

            // STEP 4: SYNTHESIZE TTS
            onStepChanged(PipelineStep.SYNTHESIZING_TTS, 55, "Google TTS দিয়ে ভয়েস তৈরি করা হচ্ছে...")
            val ttsSegmentsDir = File(workDir, "tts_segments").apply { mkdirs() }

            setupTtsListener(tts)
            tts.setSpeechRate(settings.speechRate)
            tts.setPitch(settings.pitch)

            val segmentFiles = generateTtsSegments(
                items = subtitleItems,
                tts = tts,
                outputDir = ttsSegmentsDir
            ) { current, total, text ->
                val progress = 55 + (current * 20 / total)
                onStepChanged(PipelineStep.SYNTHESIZING_TTS, progress, text)
            }

            onLog("✅ মোট ${segmentFiles.size}/${subtitleItems.size} টি ডাবিং অডিও ক্লিপ তৈরি হয়েছে।", LogLevel.SUCCESS)

            if (isCancelled) return@withContext GeneratedOutputs()

            // STEP 5: SYNC & MERGE INTO FINAL AUDIO
            onStepChanged(PipelineStep.SYNCING_MERGING, 75, "টাইমিং ও atempo স্পিড সিঙ্ক করা হচ্ছে...")
            val finalMp3File = File(workDir, "${baseName}_bangla_dubbed_audio.mp3")

            val mergeSuccess = syncAndMergeSegments(
                items = subtitleItems,
                segmentFiles = segmentFiles,
                settings = settings,
                outputFile = finalMp3File,
                workDir = workDir,
                onLog = onLog,
                onProgress = { current, total ->
                    val progress = 75 + (current * 15 / total)
                    onStepChanged(PipelineStep.SYNCING_MERGING, progress, "অডিও ট্র্যাক সিঙ্ক হচ্ছে: $current/$total...")
                }
            )

            if (!mergeSuccess || !finalMp3File.exists()) {
                throw IllegalStateException("ডাবিং অডিও ট্র্যাক মার্জ করতে ব্যর্থ হয়েছে।")
            }

            val savedBanglaAudioUri = StorageHelper.saveToDownloads(
                context,
                finalMp3File,
                "${baseName}_bangla_dubbed_audio.mp3",
                "audio/mpeg"
            )
            onLog("🎉 বাংলা ডাবিং অডিও ট্র্যাক তৈরি সম্পন্ন (${StorageHelper.formatBytes(finalMp3File.length())})", LogLevel.SUCCESS)
            onLog("📁 ফাইল সেভ হয়েছে: Download/FileTranslato/${baseName}_bangla_dubbed_audio.mp3", LogLevel.SUCCESS)

            // STEP 6: OPTIONAL VIDEO MUXING
            var savedDubbedVideoUri: Uri? = null
            var finalDubbedMkvPath: String? = null
            if (settings.muxVideoOutput) {
                onStepChanged(PipelineStep.MUXING_VIDEO, 90, "মুভিতে নতুন বাংলা অডিও যুক্ত করা হচ্ছে (Muxing)...")
                val finalDubbedVideoFile = File(workDir, "${baseName}_bangla_dubbed.mkv")

                val muxCmd = "-y -i \"$safInput\" -i \"${finalMp3File.absolutePath}\" -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k \"${finalDubbedVideoFile.absolutePath}\""
                val muxSession = FFmpegKit.execute(muxCmd)

                if (ReturnCode.isSuccess(muxSession.returnCode) && finalDubbedVideoFile.exists()) {
                    savedDubbedVideoUri = StorageHelper.saveToDownloads(
                        context,
                        finalDubbedVideoFile,
                        "${baseName}_bangla_dubbed.mkv",
                        "video/x-matroska"
                    )
                    finalDubbedMkvPath = finalDubbedVideoFile.absolutePath
                    onLog("🎬 পূর্ণাঙ্গ বাংলা ডাবিং মুভি (.mkv) তৈরি সফল!", LogLevel.SUCCESS)
                    onLog("📁 মুভি সেভ হয়েছে: Download/FileTranslato/${baseName}_bangla_dubbed.mkv", LogLevel.SUCCESS)
                } else {
                    onLog("⚠️ ভিডিও মাক্সিং করা সম্ভব হয়নি, তবে বাংলা অডিও ও সাবটাইটেল পুরোপুরি প্রস্তুত।", LogLevel.WARNING)
                }
            }

            onStepChanged(PipelineStep.COMPLETED, 100, "সমস্ত কাজ সফলভাবে সম্পন্ন হয়েছে!")

            GeneratedOutputs(
                originalAudioUri = savedAudioUri,
                originalAudioPath = extractedAudioFile.absolutePath,
                englishSrtUri = savedEngSrtUri,
                englishSrtPath = extractedEngSrtFile.absolutePath,
                banglaSrtUri = savedBanglaSrtUri,
                banglaSrtPath = banglaSrtFile.absolutePath,
                banglaDubbedAudioUri = savedBanglaAudioUri,
                banglaDubbedAudioPath = finalMp3File.absolutePath,
                dubbedMkvVideoUri = savedDubbedVideoUri,
                dubbedMkvVideoPath = finalDubbedMkvPath
            )
        } finally {
            inputHandle.close()
            // Clean up temporary wav segment files to free up disk space
            workDir.deleteRecursively()
        }
    }

    private fun setupTtsListener(tts: TextToSpeech) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) {
                    utteranceListeners[utteranceId]?.complete(true)
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId != null) {
                    utteranceListeners[utteranceId]?.complete(false)
                }
            }
        })
    }

    private suspend fun generateTtsSegments(
        items: List<SubtitleItem>,
        tts: TextToSpeech,
        outputDir: File,
        onProgress: (Int, Int, String) -> Unit
    ): Map<Int, File> = withContext(Dispatchers.IO) {
        val segmentMap = mutableMapOf<Int, File>()

        for ((idx, item) in items.withIndex()) {
            if (isCancelled) break
            val cleanText = item.translatedText.trim()
            if (cleanText.isBlank()) continue

            val segmentFile = File(outputDir, "seg_${item.index}.wav")
            val utteranceId = "UTT_${item.index}_${System.currentTimeMillis()}"
            val deferred = CompletableDeferred<Boolean>()
            utteranceListeners[utteranceId] = deferred

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val result = tts.synthesizeToFile(cleanText, params, segmentFile, utteranceId)
            if (result == TextToSpeech.SUCCESS) {
                val success = try {
                    deferred.await()
                } catch (e: Exception) {
                    false
                }
                if (success && segmentFile.exists() && segmentFile.length() > 44) {
                    segmentMap[item.index] = segmentFile
                }
            }
            utteranceListeners.remove(utteranceId)

            if (idx % 10 == 0 || idx == items.size - 1) {
                onProgress(idx + 1, items.size, "ভয়েস ক্লিপ তৈরি হচ্ছে: ${idx + 1}/${items.size}")
            }
        }
        segmentMap
    }

    /**
     * Implements time-synchronized audio assembly with auto atempo adjustment and silence gaps.
     */
    private suspend fun syncAndMergeSegments(
        items: List<SubtitleItem>,
        segmentFiles: Map<Int, File>,
        settings: DubberSettings,
        outputFile: File,
        workDir: File,
        onLog: (String, LogLevel) -> Unit,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (segmentFiles.isEmpty()) return@withContext false

        val concatDir = File(workDir, "concat_temp").apply { mkdirs() }
        val concatListFile = File(concatDir, "concat_list.txt")
        val listBuilder = StringBuilder()
        var currentTimelineMs = 0L

        var atempoAdjustCount = 0

        for ((idx, item) in items.withIndex()) {
            if (isCancelled) return@withContext false
            val segFile = segmentFiles[item.index] ?: continue

            // 1. Check if there is silence before this dialogue starts
            val gapMs = item.startTimeMs - currentTimelineMs
            if (gapMs > 60L) {
                val silenceFile = File(concatDir, "sil_${item.index}.wav")
                val durationSec = String.format(Locale.US, "%.3f", gapMs / 1000.0)
                FFmpegKit.execute("-y -f lavfi -i anullsrc=r=24000:cl=mono -t $durationSec -c:a pcm_s16le \"${silenceFile.absolutePath}\"")
                if (silenceFile.exists() && silenceFile.length() > 44) {
                    listBuilder.append("file '${silenceFile.absolutePath}'\n")
                    currentTimelineMs += gapMs
                }
            }

            // 2. Measure actual duration of the TTS audio clip
            // 24000 Hz, 16-bit mono = 48,000 bytes per second = 48 bytes per ms
            val dataBytes = (segFile.length() - 44).coerceAtLeast(0)
            val audioDurationMs = dataBytes / 48
            val allowedDurationMs = item.durationMs

            val processedSegFile = File(concatDir, "proc_${item.index}.wav")

            // 3. Audio tempo matching with atempo filter (Prompt 3)
            if (settings.enableAtempoSync && audioDurationMs > (allowedDurationMs + 150L)) {
                val rawRatio = audioDurationMs.toDouble() / allowedDurationMs.toDouble()
                val speedRatio = rawRatio.coerceIn(1.0, settings.maxAtempoSpeed.toDouble())
                val filterString = if (speedRatio > 2.0) {
                    "atempo=2.0,atempo=${String.format(Locale.US, "%.2f", speedRatio / 2.0)}"
                } else {
                    "atempo=${String.format(Locale.US, "%.2f", speedRatio)}"
                }

                val speedCmd = "-y -i \"${segFile.absolutePath}\" -filter:a \"$filterString\" -ar 24000 -ac 1 -c:a pcm_s16le \"${processedSegFile.absolutePath}\""
                FFmpegKit.execute(speedCmd)
                atempoAdjustCount++
            } else {
                // Normalize audio sample rate and channel layout
                val normCmd = "-y -i \"${segFile.absolutePath}\" -ar 24000 -ac 1 -c:a pcm_s16le \"${processedSegFile.absolutePath}\""
                FFmpegKit.execute(normCmd)
            }

            val finalFileToAppend = if (processedSegFile.exists() && processedSegFile.length() > 44) {
                processedSegFile
            } else {
                segFile
            }

            listBuilder.append("file '${finalFileToAppend.absolutePath}'\n")
            val appendedBytes = (finalFileToAppend.length() - 44).coerceAtLeast(0)
            val actualAppendedMs = appendedBytes / 48
            currentTimelineMs += actualAppendedMs

            if (idx % 15 == 0 || idx == items.size - 1) {
                onProgress(idx + 1, items.size)
            }
        }

        if (atempoAdjustCount > 0) {
            onLog("⚡ $atempoAdjustCount টি সংলাপে স্বয়ংক্রিয় atempo স্পিড প্রয়োগ করে টাইমিং ওভারল্যাপ মুক্ত করা হয়েছে।", LogLevel.INFO)
        }

        concatListFile.writeText(listBuilder.toString(), Charsets.UTF_8)
        val mergeSession = FFmpegKit.execute("-y -f concat -safe 0 -i \"${concatListFile.absolutePath}\" -c:a libmp3lame -b:a 192k \"${outputFile.absolutePath}\"")

        return@withContext ReturnCode.isSuccess(mergeSession.returnCode) && outputFile.exists() && outputFile.length() > 1024
    }

    private fun parseSrtFile(file: File): List<SubtitleItem> {
        val content = file.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val blocks = content.trim().split(Regex("\n\n+"))
        val items = mutableListOf<SubtitleItem>()
        val timePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")

        var indexCounter = 1
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size >= 2) {
                val timeLineIndex = if (lines[0].matches(Regex("^\\d+$"))) 1 else 0
                if (timeLineIndex < lines.size) {
                    val matcher = timePattern.matcher(lines[timeLineIndex])
                    if (matcher.find()) {
                        val startTimeStr = "${matcher.group(1)}:${matcher.group(2)}:${matcher.group(3)},${matcher.group(4)}"
                        val endTimeStr = "${matcher.group(5)}:${matcher.group(6)}:${matcher.group(7)},${matcher.group(8)}"

                        val startMs = timeToMillis(
                            matcher.group(1)!!.toLong(), matcher.group(2)!!.toLong(),
                            matcher.group(3)!!.toLong(), matcher.group(4)!!.toLong()
                        )
                        val endMs = timeToMillis(
                            matcher.group(5)!!.toLong(), matcher.group(6)!!.toLong(),
                            matcher.group(7)!!.toLong(), matcher.group(8)!!.toLong()
                        )

                        val text = lines.subList(timeLineIndex + 1, lines.size).joinToString(" ")
                        if (text.isNotBlank()) {
                            items.add(
                                SubtitleItem(
                                    index = indexCounter++,
                                    startTimeStr = startTimeStr,
                                    endTimeStr = endTimeStr,
                                    startTimeMs = startMs,
                                    endTimeMs = endMs,
                                    originalText = text
                                )
                            )
                        }
                    }
                }
            }
        }
        return items
    }

    private fun saveBanglaSrtFile(items: List<SubtitleItem>, outFile: File) {
        val sb = StringBuilder()
        for (item in items) {
            sb.append(item.index).append("\n")
            sb.append("${item.startTimeStr} --> ${item.endTimeStr}\n")
            sb.append(item.translatedText.ifBlank { item.originalText }).append("\n\n")
        }
        outFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun timeToMillis(h: Long, m: Long, s: Long, ms: Long): Long =
        (h * 3600000L) + (m * 60000L) + (s * 1000L) + ms
}
