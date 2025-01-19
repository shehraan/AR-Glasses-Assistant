package com.example.imagetest;

import android.content.Intent;
import android.graphics.Bitmap;
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
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

import android.util.Log;

import okhttp3.*;

public class MainActivity extends AppCompatActivity {

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
            audioFilePath = getExternalFilesDir(null).getAbsolutePath() + "/audio.wav";

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); // Use OutputFormat.MPEG_4 for .wav
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); // Use AAC encoder
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
            sendAudioToWhisperAPI(audioFilePath);
        }
    }

    private void sendAudioToWhisperAPI(String audioFilePath) {
        try {
            // Create API request to Whisper
            OkHttpClient client = new OkHttpClient();
            RequestBody audioRequestBody = RequestBody.create(MediaType.parse("audio/wav"), new File(audioFilePath));
            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "audio.wav", audioRequestBody)
                    .addFormDataPart("model", "whisper-1")

                    .build();

            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/audio/transcriptions")
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer API-KEY")
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
                            String recognizedText = jsonResponse.getString("text");
                            runOnUiThread(() -> {
                                textInput.setText(recognizedText);
                                sendToChatGPT(recognizedText, imageUri);
                            });
                        } catch (JSONException e) {
                            runOnUiThread(() -> outputTextBox.setText("Error parsing response: " + e.getMessage()));
                        }
                    } else {
                        runOnUiThread(() -> outputTextBox.setText("Error: " + response.code() + "\n" + responseBody));
                    }
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> outputTextBox.setText("Error: " + e.getMessage()));
        }
    }

    private void sendToChatGPT(String text, Uri imageUri) {
        try {
            // Convert image to Base64
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Bitmap resizedBitmap = resizeImage(bitmap, 400, 400); // Resize to 400x400 max
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
            String base64Image = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            // Construct JSON payload
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

            // Create API request
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .post(RequestBody.create(MediaType.parse("application/json"), jsonBody.toString()))
                    .addHeader("Authorization", "Bearer API-KEY")
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
                            String content = jsonResponse.getJSONArray("choices").getJSONObject(0)
                                    .getJSONObject("message").getString("content");
                            runOnUiThread(() -> {
                                outputTextBox.setText(content);
                                if (textToSpeech != null) {
                                    textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, null);
                                }
                            });
                        } catch (JSONException e) {
                            runOnUiThread(() -> outputTextBox.setText("Error parsing response: " + e.getMessage()));
                        }
                    } else {
                        runOnUiThread(() -> outputTextBox.setText("Error: " + response.code() + "\n" + responseBody));
                    }
                }
            });
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
