/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.voicebroadcast

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import im.vector.app.features.voice.AbstractVoiceRecorderQ
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.content.ContentAttachmentData

@RequiresApi(Build.VERSION_CODES.Q)
class VoiceBroadcastRecorderQ(
        context: Context,
) : AbstractVoiceRecorderQ(context), VoiceBroadcastRecorder {

    private var maxFileSize = 0L // zero or negative for no limit
    private var currentRoomId: String? = null
    override var currentSequence = 0

    override var listener: VoiceBroadcastRecorder.Listener? = null

    override val outputFormat = MediaRecorder.OutputFormat.MPEG_4
    override val audioEncoder = MediaRecorder.AudioEncoder.HE_AAC

    override val fileNameExt: String = "mp4"

    override fun initializeRecord(roomId: String, attachmentData: ContentAttachmentData?) {
        super.initializeRecord(roomId, attachmentData)
        mediaRecorder?.setMaxFileSize(maxFileSize)
        mediaRecorder?.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING -> onMaxFileSizeApproaching(roomId)
                MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED -> onNextOutputFileStarted()
                else -> Unit // Nothing to do
            }
        }
    }

    override fun startRecord(roomId: String, chunkLength: Int) {
        currentRoomId = roomId
        maxFileSize = (chunkLength * audioEncodingBitRate / 8).toLong()
        currentSequence = 1
        startRecord(roomId)
    }

    override fun pauseRecord() {
        tryOrNull { mediaRecorder?.stop() }
        mediaRecorder?.reset()
        notifyOutputFileCreated()
    }

    override fun resumeRecord() {
        currentSequence++
        currentRoomId?.let { startRecord(it) }
    }

    override fun stopRecord() {
        super.stopRecord()
        notifyOutputFileCreated()
        listener = null
        currentSequence = 0
    }

    override fun release() {
        mediaRecorder?.setOnInfoListener(null)
        super.release()
    }

    private fun onMaxFileSizeApproaching(roomId: String) {
        setNextOutputFile(roomId)
    }

    private fun onNextOutputFileStarted() {
        notifyOutputFileCreated()
        currentSequence++
    }

    private fun notifyOutputFileCreated() {
        outputFile?.let {
            listener?.onVoiceMessageCreated(it, currentSequence)
            outputFile = nextOutputFile
            nextOutputFile = null
        }
    }
}