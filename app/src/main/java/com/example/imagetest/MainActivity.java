package com.example.imagetest;
//Works!!!
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

import android.util.Log;

import okhttp3.*;

public class MainActivity extends AppCompatActivity {

    // API Key defined as a single variable
    private static final String API_KEY = "Bearer GPT_API";

    private static final int PICK_IMAGE_REQUEST = 1;

    private EditText textInput;
    private ImageView imagePreview;
    private TextView outputTextBox;
    private Uri imageUri;

    private TextToSpeech textToSpeech;

    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textInput = findViewById(R.id.textInput);
        imagePreview = findViewById(R.id.imagePreview);
        outputTextBox = findViewById(R.id.outputTextBox);

        Button selectImageButton = findViewById(R.id.selectImageButton);
        Button submitButton = findViewById(R.id.submitButton);

        // Initialize TextToSpeech
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

        // Handle image selection
        selectImageButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        });

        // Handle audio recording with Whisper API
        submitButton.setOnClickListener(v -> {
            if (!isRecording) {
                isRecording = true;
                startAudioRecording();
                submitButton.setText("Stop Recording");
            } else {
                isRecording = false;
                stopAudioRecordingAndProcess();
                submitButton.setText("Start Recording");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imageUri = data.getData();
            imagePreview.setImageURI(imageUri);
            imagePreview.setVisibility(View.VISIBLE);
        }
    }

    private MediaRecorder mediaRecorder;
    private String audioFilePath;


    private void startAudioRecording() {
        try {
            audioFilePath = getExternalFilesDir(null).getAbsolutePath() + "/audio.wav"; // Actually AAC/MP4 if not changed

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_WB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            mediaRecorder.setAudioSamplingRate(16000);
// Use .amr file extension
            audioFilePath = getExternalFilesDir(null).getAbsolutePath() + "/audio.amr";
            mediaRecorder.setOutputFile(audioFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.d("Audio", "Recording started.");
        } catch (IOException e) {
            Log.e("Audio", "Recording failed: " + e.getMessage());
        }
    }

    private void stopAudioRecordingAndProcess() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Log.d("Audio", "Recording stopped.");
            // Send to Google STT instead of Whisper
            sendAudioToGoogleSTT(audioFilePath);
        }
    }

    private void sendAudioToGoogleSTT(String audioFilePath) {
        final String apiKey = "Google_API";
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
            config.put("encoding", "AMR_WB");
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
                                runOnUiThread(() ->
                                        outputTextBox.setText("No speech recognized."));
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
                .addHeader("Authorization", "Bearer GPT_API")
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
                            // Show text from GPT
                            outputTextBox.setText(content);
                            // Now use Google TTS
                            speakWithGoogleCloudTTS(content);
                        });
                    } catch (JSONException e) {
                        runOnUiThread(() -> outputTextBox.setText("JSON parse error: " + e.getMessage()));
                    }
                } else {
                    runOnUiThread(() -> outputTextBox.setText(
                            "Error: " + response.code() + "\n" + responseBody));
                }
            }
        });
    }
    private void speakWithGoogleCloudTTS(String text) {
        final String googleTtsApiKey = "Google_API";
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
                    runOnUiThread(() ->
                            outputTextBox.setText("TTS Error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String respBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject respJson = new JSONObject(respBody);
                            String audioContent = respJson.optString("audioContent");
                            if (audioContent == null || audioContent.isEmpty()) {
                                runOnUiThread(() ->
                                        outputTextBox.setText("No audioContent in TTS response."));
                                return;
                            }
                            // Decode base64
                            byte[] audioData = android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT);
                            // Write to temp file
                            File tempAudioFile = new File(getCacheDir(), "ttsOutput.mp3");
                            FileOutputStream fos = new FileOutputStream(tempAudioFile);
                            fos.write(audioData);
                            fos.close();
                            runOnUiThread(() -> playMp3File(tempAudioFile));
                        } catch (JSONException e) {
                            runOnUiThread(() ->
                                    outputTextBox.setText("JSON parse error in TTS: " + e.getMessage()));
                        }
                    } else {
                        runOnUiThread(() ->
                                outputTextBox.setText("TTS Error: " + response.code() + "\n" + respBody));
                    }
                }
            });
        } catch (JSONException e) {
            runOnUiThread(() ->
                    outputTextBox.setText("TTS JSON error: " + e.getMessage()));
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
            // 1. If imageUri is null, either skip or handle differently
            if (imageUri == null) {
                // Option A: Just send text without an image
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

                // Then do the OkHttp request just like you were, but skipping the image part
                sendJsonToChatGPT(jsonBody);
                return;
            }

            // 2. Otherwise, the user selected an image, so proceed
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                    this.getContentResolver(),
                    imageUri
            );
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Bitmap resizedBitmap = resizeImage(bitmap, 400, 400);
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);

            String base64Image = android.util.Base64
                    .encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP);

            // 3. Build the JSON with both text and image
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

            // 4. Send to ChatGPT
            sendJsonToChatGPT(jsonBody);

        } catch (IOException | JSONException e) {
            runOnUiThread(() -> outputTextBox.setText("Error: " + e.getMessage()));
        }
    }

    private Bitmap resizeImage(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxWidth;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxHeight;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }
}
