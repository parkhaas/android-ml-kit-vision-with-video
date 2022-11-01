package com.google.mlkit.vision.demo.video;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.mlkit.vision.demo.GraphicOverlay;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.demo.java.VisionProcessorBase;
import com.google.mlkit.vision.demo.java.facedetector.FaceDetectorProcessor;
import com.google.mlkit.vision.demo.java.segmenter.SegmenterProcessor;
import com.google.mlkit.vision.demo.java.textdetector.TextRecognitionProcessor;

import java.util.ArrayList;
import java.util.List;

public abstract class VideoBaseActivity extends AppCompatActivity {
	private static final String TAG = VideoBaseActivity.class.getSimpleName();

	private static final int REQUEST_CHOOSE_VIDEO = 1001;

	private static final String FACE_DETECTION = "Face Detection";
	private static final String TEXT_RECOGNITION = "Text Recognition";
	private static final String SELFIE_SEGMENTATION = "Selfie Segmentation";

	private SimpleExoPlayer player;
	private PlayerView playerView;
	private GraphicOverlay graphicOverlay;

	private VisionProcessorBase imageProcessor;
	private String selectedProcessor = FACE_DETECTION;

	private int frameWidth, frameHeight;

	private boolean processing;
	private boolean pending;
	private Bitmap lastFrame;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_base_video);

		player = createPlayer();

		playerView = findViewById(R.id.player_view);
		playerView.setPlayer(player);
		FrameLayout contentFrame = playerView.findViewById(R.id.exo_content_frame);
		View videoFrameView = createVideoFrameView();
		if (videoFrameView != null) contentFrame.addView(videoFrameView);

		graphicOverlay = new GraphicOverlay(this, null);
		contentFrame.addView(graphicOverlay);

		populateProcessorSelector();
		findViewById(R.id.choose_btn).setOnClickListener(v -> {
			startChooseVideoIntentForResult();
		});
	}

	protected abstract @NonNull
	SimpleExoPlayer createPlayer();

	protected abstract @Nullable
	View createVideoFrameView();

	protected Size getSizeForDesiredSize(int width, int height, int desiredSize) {
		int w, h;
		if (width > height) {
			w = desiredSize;
			h = Math.round((height / (float) width) * w);
		} else {
			h = desiredSize;
			w = Math.round((width / (float) height) * h);
		}
		return new Size(w, h);
	}

	protected void processFrame(Bitmap frame) {
		lastFrame = frame;
		if (imageProcessor != null) {
			pending = processing;
			if (!processing) {
				processing = true;
				if (frameWidth != frame.getWidth() || frameHeight != frame.getHeight()) {
					frameWidth = frame.getWidth();
					frameHeight = frame.getHeight();
					graphicOverlay.setImageSourceInfo(frameWidth, frameHeight, false);
				}
				imageProcessor.setOnProcessingCompleteListener(new VisionProcessorBase.OnProcessingCompleteListener() {
					@Override
					public void onProcessingComplete() {
						processing = false;
						onProcessComplete(frame);
						if (pending) processFrame(lastFrame);
					}
				});
				imageProcessor.processBitmap(frame, graphicOverlay);
			}
		}
	}

	protected void onProcessComplete(Bitmap frame) {
	}

	@Override
	protected void onResume() {
		super.onResume();
		createImageProcessor();
	}

	@Override
	protected void onPause() {
		super.onPause();
		player.pause();
		stopImageProcessor();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		player.stop();
		player.release();
	}

	private void setupPlayer(Uri uri) {
		MediaItem mediaItem = MediaItem.fromUri(uri);
		player.stop();
		player.setMediaItem(mediaItem);
		player.prepare();
		player.play();
	}

	private void startChooseVideoIntentForResult() {
		Intent intent = new Intent();
		intent.setType("video/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_CHOOSE_VIDEO);

//		setupPlayer(Uri.parse("http://10.110.166.45:8080/hls/video12/index.m3u8"));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_CHOOSE_VIDEO && resultCode == RESULT_OK) {
			// In this case, imageUri is returned by the chooser, save it.
			setupPlayer(data.getData());
		}
	}

	private void populateProcessorSelector() {
		Spinner featureSpinner = findViewById(R.id.processor_selector);
		List<String> options = new ArrayList<>();
		options.add(FACE_DETECTION);
		options.add(TEXT_RECOGNITION);
		options.add(SELFIE_SEGMENTATION);

		// Creating adapter for featureSpinner
		ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(this, R.layout.spinner_style, options);
		// Drop down layout style - list view with radio button
		dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// attaching data adapter to spinner
		featureSpinner.setAdapter(dataAdapter);
		featureSpinner.setOnItemSelectedListener(
				new AdapterView.OnItemSelectedListener() {

					@Override
					public void onItemSelected(
							AdapterView<?> parentView, View selectedItemView, int pos, long id) {
						selectedProcessor = parentView.getItemAtPosition(pos).toString();
						createImageProcessor();
						if (lastFrame != null) processFrame(lastFrame);
					}

					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
					}
				});
	}

	private void createImageProcessor() {
		stopImageProcessor();

		try {
			switch (selectedProcessor) {
				case FACE_DETECTION:
					imageProcessor = new FaceDetectorProcessor(this);
					break;
				case TEXT_RECOGNITION:
					imageProcessor = new TextRecognitionProcessor(this);
					break;
				case SELFIE_SEGMENTATION:
					imageProcessor = new SegmenterProcessor(this, /* isStreamMode= */ true);
					break;
				default:
			}
		} catch (Exception e) {
			Log.e(TAG, "Can not create image processor: " + selectedProcessor, e);
			Toast.makeText(
							getApplicationContext(),
							"Can not create image processor: " + e.getMessage(),
							Toast.LENGTH_LONG)
					.show();
		}
	}

	private void stopImageProcessor() {
		if (imageProcessor != null) {
			imageProcessor.stop();
			imageProcessor = null;
			processing = false;
			pending = false;
		}
	}
}