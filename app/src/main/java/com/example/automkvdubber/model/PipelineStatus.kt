package com.example.automkvdubber.model

import android.net.Uri

enum class PipelineStep(val stepNumber: Int, val titleBengali: String, val titleEnglish: String) {
    IDLE(0, "অপেক্ষমাণ", "Idle"),
    PREPARING(1, "MKV ফাইল প্রস্তুতি", "Preparing Input"),
    EXTRACTING(2, "অডিও ও সাবটাইটেল নিষ্কাশন", "Extracting Tracks"),
    TRANSLATING(3, "বাংলায় অনুবাদ", "Translating Subtitles"),
    SYNTHESIZING_TTS(4, "TTS কণ্ঠস্বর তৈরি", "Synthesizing Speech"),
    SYNCING_MERGING(5, "টাইমিং সিঙ্ক ও ট্র্যাক একত্রীকরণ", "Syncing & Merging Audio"),
    MUXING_VIDEO(6, "মুভিতে নতুন ডাবিং যুক্তকরণ", "Muxing Video"),
    COMPLETED(7, "সম্পন্ন হয়েছে", "Completed"),
    ERROR(-1, "ত্রুটি ঘটেছে", "Error"),
    CANCELLED(-2, "বাতিল করা হয়েছে", "Cancelled")
}

data class DubberSettings(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val enableAtempoSync: Boolean = true,
    val maxAtempoSpeed: Float = 2.0f,
    val targetLanguageCode: String = "bn",
    val targetLanguageName: String = "বাংলা (Bengali)",
    val muxVideoOutput: Boolean = true
)

data class GeneratedOutputs(
    val originalAudioUri: Uri? = null,
    val originalAudioPath: String? = null,
    val englishSrtUri: Uri? = null,
    val englishSrtPath: String? = null,
    val banglaSrtUri: Uri? = null,
    val banglaSrtPath: String? = null,
    val banglaDubbedAudioUri: Uri? = null,
    val banglaDubbedAudioPath: String? = null,
    val dubbedMkvVideoUri: Uri? = null,
    val dubbedMkvVideoPath: String? = null
)

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}
