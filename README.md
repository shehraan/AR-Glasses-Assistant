# AR Glasses AI Assistant
#### My nanny even used this app to help her [read and learn English!](https://x.com/ShehraanH/status/1904518637039288337?s=20)


---
## Features
- **Vision-aware AI**: Captures what you see through the glasses camera and sends it to GPT-4o for context-aware answers
- **Voice input**: Records your question as audio and transcribes it via Google Cloud Speech-to-Text
- **Spoken responses**: AI answers are read aloud through Google Cloud Text-to-Speech
- **Hands-free gestures**: Single tap captures an image and starts recording; double tap captures a fresh image without recording
- **Silence detection**: Automatically stops recording after sustained silence, no button press required
- **AR glasses integration**: Supports Rayneo AR SDK temple button actions for fully hands-free control
- **Fallback touch UI**: Works with on-screen buttons for testing on a standard Android device
---
## Tech Stack
| Layer          | Technology                                |
|----------------|-------------------------------------------|
| Platform       | Android 9+ (API 28+)                      |
| AI / Vision    | OpenAI GPT-4o (multimodal)                |
| TTS, STT       | Google Cloud API                          |
| AR Hardware    | Rayneo AR Glasses (AR SDK)                |
| Audio Format   | 16-bit PCM WAV (16 kHz, mono)             |
| Image Format   | JPEG (base64-encoded, resized to 400×400) |
---
## How It Works
```
┌─────────────────────────────────────────────────────────────┐
│                         User Action                          │
│         (tap temple button / wake action / tap screen)        │
└────────────────────────────┬────────────────────────────────┘
                             │
                    ┌────────▼─────────┐
                    │  Capture Image   │
                    │  (Camera API)    │
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
                    │  Record Voice    │
                    │  (AudioRecord,   │
                    │   WAV, 16 kHz)   │
                    └────────┬─────────┘
                             │
                    ┌────────▼──────────────┐
                    │  Google Cloud STT     │
                    │  (speech → text)      │
                    └────────┬──────────────┘
                             │
                    ┌────────▼──────────────┐
                    │  OpenAI GPT-4o        │
                    │  (text + image → AI   │
                    │   response)           │
                    └────────┬──────────────┘
                             │
                    ┌────────▼──────────────┐
                    │  Google Cloud TTS     │
                    │  (text → MP3 speech)  │
                    └────────┬──────────────┘
                             │
                    ┌────────▼──────────────┐
                    │  Played back through  │
                    │  glasses speaker      │
                    └───────────────────────┘
```
---
## Setup
### Prerequisites
- Android Studio (Flamingo or newer)
- Android device or Rayneo AR glasses running Android API 28+
- API keys for:
    - [OpenAI Platform](https://platform.openai.com/) (GPT-4o access required)
    - [Google Cloud](https://console.cloud.google.com/) with **Speech-to-Text** and **Text-to-Speech** APIs enabled
### Configuration
Before building, replace the placeholder API keys in `MainActivity.java`:
```java
// OpenAI key
private static final String API_KEY = "******";
// Google Cloud key (used for both STT and TTS)
final String apiKey = "YOUR_GOOGLE_CLOUD_API_KEY";
```
> ⚠️ **Never commit real API keys to source control.** Consider storing them in `local.properties` or Android's secrets Gradle plugin.
### Build & Install
```bash
# Clone the repository
git clone https://github.com/shehraan/AR-Glasses-Assistant.git
cd AR-Glasses-Assistant
# Open in Android Studio and sync Gradle, then:
./gradlew assembleDebug
# Install on connected device/glasses
adb install app/build/outputs/apk/debug/app-debug.apk
```
---
## Usage
| Action | Result |
|---|---|
| **Single tap** (screen or glasses TP) | Captures image → starts voice recording |
| **Double tap** (screen or glasses TP) | Captures a fresh image (resets context) |
| Tap **"Ask with voice"** button | Toggles voice recording on/off |
| **Stop speaking** (silence for ~1 s) | Automatically stops recording and sends query |
The app will display the AI response as text and read it aloud through the speakers.
---
## Permissions Required
- `CAMERA` — to capture what you're looking at
- `RECORD_AUDIO` — to record your voice question
- `INTERNET` — to reach the OpenAI and Google Cloud APIs
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` — to save captured images temporarily
---