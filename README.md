# fluxBoard ⌨️ 🤖

An advanced, privacy-focused Android keyboard featuring real-time AI capabilities powered by **Nvidia Llama 3.3-70b-instruct**, smooth Gboard-style typing mechanics, and a categorized emoji panel.

## Features ✨

- **Nvidia Llama 3.3 Integration:** Rephrase, grammar check, summarize, or translate your text in real time with zero spelling or prediction mistakes.
- **Smart Prediction & Contractions:** Custom bigram prediction engine with automated contraction correction (e.g., `dont` -> `don't`).
- **Gboard-Style Keyboard Shortcuts:** Long-press spacebar to switch keyboards instantly, and long-press letter keys to input superscript numbers.
- **Smooth Categorized Emojis:** Hardware-accelerated scrolling emoji panel sorted by Smileys, Gestures, Hearts, Food, Travel, and Objects.
- **Trial & Key System:** 14-day free trial on the default Nvidia Llama service, with unlimited usage when adding your personal API key in settings.
- **Fully Open-Source & Private:** No tracking, telemetry, or server-side keystore caching.

---

## Getting Started (Developers) 🛠️

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Jellyfish or newer)
- Android SDK 24+

### Setup Instructions

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/tharikhe/fluxboard-landing.git
   ```

2. **Configure API Keys:**
   Create a `.env` file at the root of the project (this is excluded from Git via `.gitignore` to protect your credentials):
   ```env
   DEFAULT_API_KEY=your_nvidia_llama_api_key
   ```

3. **Import to Android Studio:**
   - Open Android Studio.
   - Choose **Open** and select the root directory of this project.
   - Let Gradle sync and resolve dependencies.

4. **Run the Project:**
   Build and run the project on an emulator or physical Android device.

---

## License 📄

This project is open-source and licensed under the [MIT License](LICENSE).
