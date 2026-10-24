package io.github.maniramezan.kommon.speech

/** Which recognizer a recognition run actually used. */
public enum class RecognizerKind {
    /**
     * A dedicated on-device recognizer (`SpeechRecognizer.createOnDeviceSpeechRecognizer`). Audio
     * stays on the device.
     */
    OnDevice,

    /**
     * The system's default recognition service. It may stream audio to remote servers, even when
     * offline recognition is preferred.
     */
    DefaultService,
}
