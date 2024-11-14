package com.example.musicarep;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1;
    private static final int REQUEST_CODE_PICK_AUDIO = 2; // Código de solicitud para el selector de archivos
    private MediaPlayer mediaPlayer;
    private Spinner spinnerSongs;
    private ArrayList<String> songNames = new ArrayList<>();
    private ArrayList<String> songPaths = new ArrayList<>();
    private int currentSongIndex = -1;
    private Button buttonSearch;
    private AudioManager audioManager;
    private SeekBar progressSeekBar;
    private TextView currentTimeTextView;
    private TextView totalTimeTextView;
    private Timer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerSongs = findViewById(R.id.spinnerSongs);
        Button buttonPlay = findViewById(R.id.buttonPlay);
        Button buttonPause = findViewById(R.id.buttonPause);
        Button buttonStop = findViewById(R.id.buttonStop);
        Button buttonForward = findViewById(R.id.buttonForward);
        Button buttonRewind = findViewById(R.id.buttonRewind);
        buttonSearch = findViewById(R.id.buttonSearch);
        SeekBar volumeSeekBar = findViewById(R.id.volumeSeekBar);
        progressSeekBar = findViewById(R.id.progressSeekBar);
        currentTimeTextView = findViewById(R.id.currentTimeTextView);
        totalTimeTextView = findViewById(R.id.totalTimeTextView);

        // Inicializar el AudioManager para controlar el volumen
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        volumeSeekBar.setMax(maxVolume);
        volumeSeekBar.setProgress(currentVolume);

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Inicializar canciones predeterminadas
        songNames.add("song1");
        songNames.add("song2");
        songPaths.add("android.resource://" + getPackageName() + "/raw/song1");
        songPaths.add("android.resource://" + getPackageName() + "/raw/song2");

        // Crear el adaptador para el Spinner con las canciones predeterminadas
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, songNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSongs.setAdapter(adapter);

        // Manejar la selección en el Spinner
        spinnerSongs.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    if (timer != null) {
                        timer.cancel();
                    }
                }

                // Inicializar el MediaPlayer con la canción seleccionada
                currentSongIndex = position;
                mediaPlayer = MediaPlayer.create(MainActivity.this, Uri.parse(songPaths.get(position)));
                initializeProgress();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Configurar el botón Play
        buttonPlay.setOnClickListener(v -> {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                startProgressUpdater();
            }
        });

        // Configurar el botón Pause
        buttonPause.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                if (timer != null) {
                    timer.cancel();
                }
            }
        });

        // Configurar el botón Stop
        buttonStop.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer = MediaPlayer.create(MainActivity.this, Uri.parse(songPaths.get(currentSongIndex)));
                if (timer != null) {
                    timer.cancel();
                }
                progressSeekBar.setProgress(0);
                currentTimeTextView.setText("0:00");
            }
        });

        // Configurar el botón Forward (avanzar 10 segundos)
        buttonForward.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                int newPosition = currentPosition + 10000; // Avanzar 10 segundos
                if (newPosition > mediaPlayer.getDuration()) {
                    newPosition = mediaPlayer.getDuration();
                }
                mediaPlayer.seekTo(newPosition);
                progressSeekBar.setProgress(newPosition);
                currentTimeTextView.setText(formatTime(newPosition));
            }
        });

        // Configurar el botón Rewind (retroceder 10 segundos)
        buttonRewind.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int currentPosition = mediaPlayer.getCurrentPosition();
                int newPosition = currentPosition - 10000; // Retroceder 10 segundos
                if (newPosition < 0) {
                    newPosition = 0;
                }
                mediaPlayer.seekTo(newPosition);
                progressSeekBar.setProgress(newPosition);
                currentTimeTextView.setText(formatTime(newPosition));
            }
        });

        // Configurar el botón de buscar canciones
        buttonSearch.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            } else {
                openFilePicker();
            }
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*"); // Filtrar solo archivos de audio
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_PICK_AUDIO);
    }


    @Override

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_AUDIO && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri audioUri = data.getData();
                String fileName = getFileName(audioUri);

                if (fileName != null && !songNames.contains(fileName)) {
                    songNames.add(fileName);
                    songPaths.add(audioUri.toString());

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, songNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerSongs.setAdapter(adapter);
                }
            }
        }
    }


    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void initializeProgress() {
        int totalDuration = mediaPlayer.getDuration();
        progressSeekBar.setMax(totalDuration);
        totalTimeTextView.setText(formatTime(totalDuration));

        progressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    currentTimeTextView.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void startProgressUpdater() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        int currentPosition = mediaPlayer.getCurrentPosition();
                        progressSeekBar.setProgress(currentPosition);
                        currentTimeTextView.setText(formatTime(currentPosition));
                    }
                });
            }
        }, 0, 1000);
    }

    private String formatTime(int milliseconds) {
        int minutes = (milliseconds / 1000) / 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (timer != null) {
            timer.cancel();
        }
        super.onDestroy();
    }
}
