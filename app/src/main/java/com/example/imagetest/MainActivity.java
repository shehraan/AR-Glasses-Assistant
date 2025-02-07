package com.example.imagetest;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera; // Deprecated—but used here for in-app capture
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.rayneo.arsdk.android.touch.TempleAction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // API Key defined as a single variable
    private static final String API_KEY = "Bearer chatapik";
    private static final int PERMISSION_REQUEST_CODE = 123;

    // UI Elements
    private EditText textInput;
    private ImageView imagePreview;
    private TextView outputTextBox;
    private Button selectImageButton;
    private Button submitButton;
    // This Uri will be used to hold the captured image’s location (if any)
    private Uri imageUri;

    private TextToSpeech textToSpeech;

    // For audio recording using AudioRecord (for WAV output)
    private AudioRecord recorder;
    private Thread recordingThread;
    private boolean isRecordingAudio = false;
    // The output WAV file path
    private String audioFilePath;

    // GestureDetector to capture single and double tap gestures from the glasses
    private GestureDetector gestureDetector;

    protected void onTempleAction(TempleAction action) {
        if (action instanceof TempleAction.DoubleClick) {
            // Instead of exiting the app, capture an image.
            captureImageAutomatically();
            // Do not call super, so finish() is not triggered.
        } else {
            finish();
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request necessary permissions
        checkAndRequestPermissions();

        // Initialize UI elements (store buttons as fields so we can update them from gesture callbacks)
        textInput = findViewById(R.id.textInput);
        imagePreview = findViewById(R.id.imagePreview);
        outputTextBox = findViewById(R.id.outputTextBox);
        selectImageButton = findViewById(R.id.selectImageButton);
        submitButton = findViewById(R.id.submitButton);

        // (Optional) Initialize TextToSpeech here if needed.
        /*
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.9f);
                Log.d("TTS", "TextToSpeech initialized successfully.");
            } else {
                textToSpeech = null;
                Log.e("TTS", "TextToSpeech initialization failed.");
            }
        });
        */

        // Set up button onClick listeners (in case a user touches the screen directly)
        selectImageButton.setOnClickListener(v -> {
            // Only allow image capture if audio recording is not in progress
            if (!isRecordingAudio) {
                captureImageAutomatically();
            } else {
                Toast.makeText(MainActivity.this, "Stop recording before capturing an image", Toast.LENGTH_SHORT).show();
            }
        });

        submitButton.setOnClickListener(v -> toggleAudioRecording());

        // Initialize our gesture detector to listen for glasses tap gestures
        gestureDetector = new GestureDetector(this, new MyGestureListener());
    }

    // Override onTouchEvent so that glasses' TP events can be processed by our gesture detector.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    /**
     * Toggles audio recording. If not recording, starts recording and changes button text.
     * If already recording, stops recording and processes the audio.
     */
    private void toggleAudioRecording() {
        if (!isRecordingAudio) {
            startAudioRecording();
            submitButton.setText("Stop Recording");
        } else {
            stopAudioRecordingAndProcess();
            submitButton.setText("Start Recording");
        }
    }

    // --- GESTURE DETECTION ---

    /**
     * A custom gesture listener to detect single and double taps.
     */
    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            toggleAudioRecording();
            return true; // Consume the event.
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (!isRecordingAudio) {
                captureImageAutomatically();
            } else {
                Toast.makeText(MainActivity.this, "Stop recording before capturing an image", Toast.LENGTH_SHORT).show();
            }
            return true; // This should consume the event so no default action occurs.
        }
    }


    // --- IMAGE CAPTURE (in‑app) using deprecated Camera API ---

    /**
     * Captures an image automatically within the app.
     * Uses a dummy SurfaceTexture so no preview is shown.
     */
    private void captureImageAutomatically() {
        Camera camera = Camera.open();
        try {
            SurfaceTexture dummySurfaceTexture = new SurfaceTexture(10);
            camera.setPreviewTexture(dummySurfaceTexture);
            camera.startPreview();
            // Capture image immediately
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    // Decode JPEG data to Bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    runOnUiThread(() -> {
                        imagePreview.setImageBitmap(bitmap);
                        imagePreview.setVisibility(View.VISIBLE);
                    });
                    // Save the bitmap to a file for later use (e.g., when sending to ChatGPT)
                    File imageFile = saveBitmapToFile(bitmap);
                    if (imageFile != null) {
                        imageUri = Uri.fromFile(imageFile);
                    }
                    camera.release();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            camera.release();
            Toast.makeText(this, "Error capturing image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Saves a Bitmap as a JPEG file in the app's external pictures directory.
     *
     * @param bitmap The Bitmap to save.
     * @return The saved File, or null if there was an error.
     */
    private File saveBitmapToFile(Bitmap bitmap) {
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        String fileName = "captured_" + System.currentTimeMillis() + ".jpg";
        File imageFile = new File(storageDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            return imageFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // --- AUDIO RECORDING using AudioRecord (WAV output) ---

    /**
     * Starts audio recording using AudioRecord.
     * The recorded raw PCM data is written to a .wav file in a background thread.
     */
    private void startAudioRecording() {
        int sampleRate = 16000; // 16 kHz
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        recorder = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
        recorder.startRecording();
        isRecordingAudio = true;
        audioFilePath = getExternalFilesDir(null).getAbsolutePath() + "/audio.wav";
        recordingThread = new Thread(() -> writeAudioDataToFile(audioFilePath, bufferSize), "AudioRecorder Thread");
        recordingThread.start();
        Log.d("Audio", "Recording started.");
    }

    /**
     * Writes the raw audio data to a file and later updates the WAV header.
     *
     * @param filePath   The path of the file to write.
     * @param bufferSize The size of the audio buffer.
     */
    private void writeAudioDataToFile(String filePath, int bufferSize) {
        byte[] data = new byte[bufferSize];
        File file = new File(filePath);
        RandomAccessFile raf = null;
        long totalAudioLen = 0;
        try {
            raf = new RandomAccessFile(file, "rw");
            // Write a placeholder for the WAV header (44 bytes)
            byte[] placeholder = new byte[44];
            raf.write(placeholder);
            while (isRecordingAudio) {
                int read = recorder.read(data, 0, bufferSize);
                if (read > 0) {
                    raf.write(data, 0, read);
                    totalAudioLen += read;
                }
            }
            long totalDataLen = totalAudioLen + 36;
            int sampleRate = 16000;
            int channels = 1;
            int byteRate = sampleRate * channels * 16 / 8;
            // Go back and write the WAV header at the beginning of the file
            raf.seek(0);
            writeWavHeader(raf, totalAudioLen, totalDataLen, sampleRate, channels, byteRate);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Writes the WAV file header to the given RandomAccessFile.
     *
     * @param raf           The RandomAccessFile (positioned at the start).
     * @param totalAudioLen Total length of the audio data.
     * @param totalDataLen  Total data length (audio + header).
     * @param sampleRate    The sample rate (e.g., 16000).
     * @param channels      Number of channels (e.g., 1 for mono).
     * @param byteRate      Byte rate (sampleRate * channels * bitsPerSample/8).
     * @throws IOException If an I/O error occurs.
     */
    private void writeWavHeader(RandomAccessFile raf, long totalAudioLen, long totalDataLen, int sampleRate, int channels, int byteRate) throws IOException {
        byte[] header = new byte[44];
        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // "fmt " chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // Subchunk1Size for PCM
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;   // PCM format
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8);  // Block align
        header[33] = 0;
        header[34] = 16; // Bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);
        raf.write(header, 0, 44);
    }

    /**
     * Stops the audio recording, waits for the recording thread to finish,
     * and then sends the WAV file to Google STT.
     */
    private void stopAudioRecordingAndProcess() {
        if (recorder != null) {
            isRecordingAudio = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d("Audio", "Recording stopped.");
            // Now send the .wav file to Google STT
            sendAudioToGoogleSTT(audioFilePath);
        }
    }

    // --- GOOGLE STT, CHATGPT, TTS, and OTHER METHODS (unchanged) ---

    private void sendAudioToGoogleSTT(String audioFilePath) {
        final String apiKey = "googleapik";
        final String url = "https://speech.googleapis.com/v1/speech:recognize?key=" + apiKey;

        try {
            File audioFile = new File(audioFilePath);
            byte[] audioBytes = new byte[(int) audioFile.length()];
            FileInputStream fis = new FileInputStream(audioFile);
            fis.read(audioBytes);
            fis.close();

            String base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP);

            JSONObject config = new JSONObject();
            config.put("languageCode", "en-US");
            // For WAV (LINEAR16) files, use "LINEAR16" as the encoding
            config.put("encoding", "LINEAR16");
            config.put("sampleRateHertz", 16000);

            JSONObject audio = new JSONObject();
            audio.put("content", base64Audio);

            JSONObject requestJson = new JSONObject();
            requestJson.put("config", config);
            requestJson.put("audio", audio);

            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestJson.toString()
            );

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() ->
                            outputTextBox.setText("Error (Google STT): " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            JSONArray resultsArray = jsonResponse.optJSONArray("results");
                            if (resultsArray != null && resultsArray.length() > 0) {
                                JSONObject firstResult = resultsArray.getJSONObject(0);
                                JSONArray alternatives = firstResult.getJSONArray("alternatives");
                                JSONObject firstAlternative = alternatives.getJSONObject(0);
                                final String recognizedText = firstAlternative.getString("transcript");

                                runOnUiThread(() -> {
                                    textInput.setText(recognizedText);
                                    sendToChatGPT(recognizedText, imageUri);
                                });
                            } else {
                                runOnUiThread(() -> outputTextBox.setText("No speech recognized."));
                            }
                        } catch (JSONException e) {
                            runOnUiThread(() ->
                                    outputTextBox.setText("Error parsing Google STT response: " + e.getMessage()));
                        }
                    } else {
                        runOnUiThread(() ->
                                outputTextBox.setText("Google STT Error: " + response.code() + "\n" + responseBody));
                    }
                }
            });
        } catch (Exception e) {
            runOnUiThread(() ->
                    outputTextBox.setText("Error (Google STT): " + e.getMessage()));
        }
    }

    private void sendJsonToChatGPT(JSONObject jsonBody) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(RequestBody.create(
                        MediaType.parse("application/json"),
                        jsonBody.toString())
                )
                .addHeader("Authorization", "Bearer chatapik")
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> outputTextBox.setText("Error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String content = jsonResponse.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        runOnUiThread(() -> {
                            outputTextBox.setText(content);
                            speakWithGoogleCloudTTS(content);
                        });
                    } catch (JSONException e) {
                        runOnUiThread(() -> outputTextBox.setText("JSON parse error: " + e.getMessage()));
                    }
                } else {
                    runOnUiThread(() -> outputTextBox.setText("Error: " + response.code() + "\n" + responseBody));
                }
            }
        });
    }

    private void speakWithGoogleCloudTTS(String text) {
        final String googleTtsApiKey = "googleapik";
        final String ttsEndpoint = "https://texttospeech.googleapis.com/v1/text:synthesize?key=" + googleTtsApiKey;

        try {
            JSONObject inputObj = new JSONObject();
            inputObj.put("text", text);

            JSONObject voiceObj = new JSONObject();
            voiceObj.put("languageCode", "en-US");
            voiceObj.put("ssmlGender", "FEMALE");

            JSONObject audioConfigObj = new JSONObject();
            audioConfigObj.put("audioEncoding", "MP3");

            JSONObject requestBodyJson = new JSONObject();
            requestBodyJson.put("input", inputObj);
            requestBodyJson.put("voice", voiceObj);
            requestBodyJson.put("audioConfig", audioConfigObj);

            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBodyJson.toString()
            );

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(ttsEndpoint)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> outputTextBox.setText("TTS Error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String respBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject respJson = new JSONObject(respBody);
                            String audioContent = respJson.optString("audioContent");
                            if (audioContent == null || audioContent.isEmpty()) {
                                runOnUiThread(() -> outputTextBox.setText("No audioContent in TTS response."));
                                return;
                            }
                            byte[] audioData = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT);
                            File tempAudioFile = new File(getCacheDir(), "ttsOutput.mp3");
                            try (FileOutputStream fos = new FileOutputStream(tempAudioFile)) {
                                fos.write(audioData);
                                fos.flush();
                            }
                            runOnUiThread(() -> playMp3File(tempAudioFile));
                        } catch (JSONException e) {
                            runOnUiThread(() -> outputTextBox.setText("JSON parse error in TTS: " + e.getMessage()));
                        }
                    } else {
                        runOnUiThread(() -> outputTextBox.setText("TTS Error: " + response.code() + "\n" + respBody));
                    }
                }
            });
        } catch (JSONException e) {
            runOnUiThread(() -> outputTextBox.setText("TTS JSON error: " + e.getMessage()));
        }
    }

    private void playMp3File(File mp3File) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(mp3File.getAbsolutePath());
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (IOException e) {
            e.printStackTrace();
            outputTextBox.setText("Error playing TTS audio: " + e.getMessage());
        }
    }

    private void sendToChatGPT(String text, Uri imageUri) {
        try {
            if (imageUri == null) {
                // Construct JSON for text only
                JSONObject textContent = new JSONObject();
                textContent.put("type", "text");
                textContent.put("text", text);

                JSONArray contentArray = new JSONArray();
                contentArray.put(textContent);

                JSONObject messageObject = new JSONObject();
                messageObject.put("role", "user");
                messageObject.put("content", contentArray);

                JSONArray messages = new JSONArray();
                messages.put(messageObject);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("model", "gpt-4o");
                jsonBody.put("messages", messages);

                sendJsonToChatGPT(jsonBody);
                return;
            }

            // Otherwise, process the image as well as text.
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Bitmap resizedBitmap = resizeImage(bitmap, 400, 400);
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);

            String base64Image = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP);

            JSONObject imageObject = new JSONObject();
            imageObject.put("url", "data:image/jpeg;base64," + base64Image);

            JSONArray contentArray = new JSONArray();
            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", text);

            JSONObject imageContent = new JSONObject();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageObject);

            contentArray.put(textContent);
            contentArray.put(imageContent);

            JSONObject messageObject = new JSONObject();
            messageObject.put("role", "user");
            messageObject.put("content", contentArray);

            JSONArray messages = new JSONArray();
            messages.put(messageObject);

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "gpt-4o");
            jsonBody.put("messages", messages);

            sendJsonToChatGPT(jsonBody);

        } catch (IOException | JSONException e) {
            runOnUiThread(() -> outputTextBox.setText("Error: " + e.getMessage()));
        }
    }

    private Bitmap resizeImage(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float bitmapRatio = (float) width / height;
        if (bitmapRatio > 1) {
            width = maxWidth;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxHeight;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    // --- PERMISSION CHECKING ---

    private void checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    listPermissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this,
                        "Some permissions are not granted. The app may not work properly.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
