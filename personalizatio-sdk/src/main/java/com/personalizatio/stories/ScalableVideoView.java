package com.personalizatio.stories;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.personalizatio.R;
import com.personalizatio.SDK;

import java.io.IOException;

final class ScalableVideoView extends TextureView implements TextureView.SurfaceTextureListener, MediaPlayer.OnVideoSizeChangedListener {

	private MediaPlayer mMediaPlayer;
	private ScalableType mScalableType = ScalableType.NONE;

	public ScalableVideoView(Context context) {
		this(context, null);
	}

	public ScalableVideoView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ScalableVideoView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		if( attrs == null ) {
			return;
		}

		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.scaleStyle, 0, 0);
		if( a == null ) {
			return;
		}

		int scaleType = a.getInt(R.styleable.scaleStyle_scalableType, ScalableType.NONE.ordinal());
		a.recycle();
		mScalableType = ScalableType.values()[scaleType];
	}

	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
		Surface surface = new Surface(surfaceTexture);
		if( mMediaPlayer != null ) {
			mMediaPlayer.setSurface(surface);
		}
	}

	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
	}

	@Override
	public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		return false;
	}

	@Override
	public void onSurfaceTextureUpdated(SurfaceTexture surface) {
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if( mMediaPlayer == null ) {
			return;
		}

		if( isPlaying() ) {
			mMediaPlayer.pause();
		}
//		release();
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		scaleVideoSize(width, height);
	}

	private void scaleVideoSize(int videoWidth, int videoHeight) {
		if( videoWidth == 0 || videoHeight == 0 ) {
			return;
		}

		Size viewSize = new Size(getWidth(), getHeight());
		Size videoSize = new Size(videoWidth, videoHeight);
		ScaleManager scaleManager = new ScaleManager(viewSize, videoSize);
		Matrix matrix = scaleManager.getScaleMatrix(mScalableType);
		if( matrix != null ) {
			setTransform(matrix);
		}
	}

	private void initializeMediaPlayer() {
		if( mMediaPlayer == null ) {
			mMediaPlayer = new MediaPlayer();
			mMediaPlayer.setOnVideoSizeChangedListener(this);
			setSurfaceTextureListener(this);
		} else {
			reset();
		}
	}

	public void setDataSource(@NonNull String path) throws IOException {
		initializeMediaPlayer();
		mMediaPlayer.setDataSource(path);
	}

	public void setScalableType(ScalableType scalableType) {
		mScalableType = scalableType;
		scaleVideoSize(getVideoWidth(), getVideoHeight());
	}

	public void prepare(@Nullable MediaPlayer.OnPreparedListener listener)
			throws IOException, IllegalStateException {
		mMediaPlayer.setOnPreparedListener(listener);
		mMediaPlayer.prepare();
	}

	public void prepareAsync(@Nullable MediaPlayer.OnPreparedListener listener)
			throws IllegalStateException {
		mMediaPlayer.setOnPreparedListener(listener);
		mMediaPlayer.prepareAsync();
	}

	public void prepare() throws IOException, IllegalStateException {
		prepare(null);
	}

	public void prepareAsync() throws IllegalStateException {
		prepareAsync(null);
	}

	public void setOnErrorListener(@Nullable MediaPlayer.OnErrorListener listener) {
		mMediaPlayer.setOnErrorListener(listener);
	}

	public void setOnCompletionListener(@Nullable MediaPlayer.OnCompletionListener listener) {
		mMediaPlayer.setOnCompletionListener(listener);
	}

	public void setOnInfoListener(@Nullable MediaPlayer.OnInfoListener listener) {
		mMediaPlayer.setOnInfoListener(listener);
	}

	public int getVideoHeight() {
		return mMediaPlayer.getVideoHeight();
	}

	public int getVideoWidth() {
		return mMediaPlayer.getVideoWidth();
	}

	public boolean isPlaying() {
		return mMediaPlayer.isPlaying();
	}

	public void stop() {
		mMediaPlayer.stop();
	}

	public void reset() {
		mMediaPlayer.reset();
	}

	public void release() {
		if( mMediaPlayer != null ) {
			reset();
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
	}

	enum ScalableType {
		NONE,

		FIT_XY,
		FIT_START,
		FIT_CENTER,
		FIT_END,

		LEFT_TOP,
		LEFT_CENTER,
		LEFT_BOTTOM,
		CENTER_TOP,
		CENTER,
		CENTER_BOTTOM,
		RIGHT_TOP,
		RIGHT_CENTER,
		RIGHT_BOTTOM,

		LEFT_TOP_CROP,
		LEFT_CENTER_CROP,
		LEFT_BOTTOM_CROP,
		CENTER_TOP_CROP,
		CENTER_CROP,
		CENTER_BOTTOM_CROP,
		RIGHT_TOP_CROP,
		RIGHT_CENTER_CROP,
		RIGHT_BOTTOM_CROP,

		START_INSIDE,
		CENTER_INSIDE,
		END_INSIDE
	}

	enum PivotPoint {
		LEFT_TOP,
		LEFT_CENTER,
		LEFT_BOTTOM,
		CENTER_TOP,
		CENTER,
		CENTER_BOTTOM,
		RIGHT_TOP,
		RIGHT_CENTER,
		RIGHT_BOTTOM
	}

	static class Size {

		private final int mWidth;
		private final int mHeight;

		public Size(int width, int height) {
			mWidth = width;
			mHeight = height;
		}

		public int getWidth() {
			return mWidth;
		}

		public int getHeight() {
			return mHeight;
		}
	}

	static class ScaleManager {

		private final Size mViewSize;
		private final Size mVideoSize;

		public ScaleManager(Size viewSize, Size videoSize) {
			mViewSize = viewSize;
			mVideoSize = videoSize;
		}

		public Matrix getScaleMatrix(ScalableType scalableType) {
			switch (scalableType) {
				case NONE:
					return getNoScale();

				case FIT_XY:
					return fitXY();
				case FIT_CENTER:
					return fitCenter();
				case FIT_START:
					return fitStart();
				case FIT_END:
					return fitEnd();

				case LEFT_TOP:
					return getOriginalScale(PivotPoint.LEFT_TOP);
				case LEFT_CENTER:
					return getOriginalScale(PivotPoint.LEFT_CENTER);
				case LEFT_BOTTOM:
					return getOriginalScale(PivotPoint.LEFT_BOTTOM);
				case CENTER_TOP:
					return getOriginalScale(PivotPoint.CENTER_TOP);
				case CENTER:
					return getOriginalScale(PivotPoint.CENTER);
				case CENTER_BOTTOM:
					return getOriginalScale(PivotPoint.CENTER_BOTTOM);
				case RIGHT_TOP:
					return getOriginalScale(PivotPoint.RIGHT_TOP);
				case RIGHT_CENTER:
					return getOriginalScale(PivotPoint.RIGHT_CENTER);
				case RIGHT_BOTTOM:
					return getOriginalScale(PivotPoint.RIGHT_BOTTOM);

				case LEFT_TOP_CROP:
					return getCropScale(PivotPoint.LEFT_TOP);
				case LEFT_CENTER_CROP:
					return getCropScale(PivotPoint.LEFT_CENTER);
				case LEFT_BOTTOM_CROP:
					return getCropScale(PivotPoint.LEFT_BOTTOM);
				case CENTER_TOP_CROP:
					return getCropScale(PivotPoint.CENTER_TOP);
				case CENTER_CROP:
					return getCropScale(PivotPoint.CENTER);
				case CENTER_BOTTOM_CROP:
					return getCropScale(PivotPoint.CENTER_BOTTOM);
				case RIGHT_TOP_CROP:
					return getCropScale(PivotPoint.RIGHT_TOP);
				case RIGHT_CENTER_CROP:
					return getCropScale(PivotPoint.RIGHT_CENTER);
				case RIGHT_BOTTOM_CROP:
					return getCropScale(PivotPoint.RIGHT_BOTTOM);

				case START_INSIDE:
					return startInside();
				case CENTER_INSIDE:
					return centerInside();
				case END_INSIDE:
					return endInside();

				default:
					return null;
			}
		}

		private Matrix getMatrix(float sx, float sy, float px, float py) {
			Matrix matrix = new Matrix();
			matrix.setScale(sx, sy, px, py);
			return matrix;
		}

		private Matrix getMatrix(float sx, float sy, PivotPoint pivotPoint) {
			switch (pivotPoint) {
				case LEFT_TOP:
					return getMatrix(sx, sy, 0, 0);
				case LEFT_CENTER:
					return getMatrix(sx, sy, 0, mViewSize.getHeight() / 2f);
				case LEFT_BOTTOM:
					return getMatrix(sx, sy, 0, mViewSize.getHeight());
				case CENTER_TOP:
					return getMatrix(sx, sy, mViewSize.getWidth() / 2f, 0);
				case CENTER:
					return getMatrix(sx, sy, mViewSize.getWidth() / 2f, mViewSize.getHeight() / 2f);
				case CENTER_BOTTOM:
					return getMatrix(sx, sy, mViewSize.getWidth() / 2f, mViewSize.getHeight());
				case RIGHT_TOP:
					return getMatrix(sx, sy, mViewSize.getWidth(), 0);
				case RIGHT_CENTER:
					return getMatrix(sx, sy, mViewSize.getWidth(), mViewSize.getHeight() / 2f);
				case RIGHT_BOTTOM:
					return getMatrix(sx, sy, mViewSize.getWidth(), mViewSize.getHeight());
				default:
					throw new IllegalArgumentException("Illegal PivotPoint");
			}
		}

		private Matrix getNoScale() {
			float sx = mVideoSize.getWidth() / (float) mViewSize.getWidth();
			float sy = mVideoSize.getHeight() / (float) mViewSize.getHeight();
			return getMatrix(sx, sy, PivotPoint.LEFT_TOP);
		}

		private Matrix getFitScale(PivotPoint pivotPoint) {
			float sx = (float) mViewSize.getWidth() / mVideoSize.getWidth();
			float sy = (float) mViewSize.getHeight() / mVideoSize.getHeight();
			float minScale = Math.min(sx, sy);
			sx = minScale / sx;
			sy = minScale / sy;
			return getMatrix(sx, sy, pivotPoint);
		}

		private Matrix fitXY() {
			return getMatrix(1, 1, PivotPoint.LEFT_TOP);
		}

		private Matrix fitStart() {
			return getFitScale(PivotPoint.LEFT_TOP);
		}

		private Matrix fitCenter() {
			return getFitScale(PivotPoint.CENTER);
		}

		private Matrix fitEnd() {
			return getFitScale(PivotPoint.RIGHT_BOTTOM);
		}

		private Matrix getOriginalScale(PivotPoint pivotPoint) {
			float sx = mVideoSize.getWidth() / (float) mViewSize.getWidth();
			float sy = mVideoSize.getHeight() / (float) mViewSize.getHeight();
			return getMatrix(sx, sy, pivotPoint);
		}

		private Matrix getCropScale(PivotPoint pivotPoint) {
			float sx = (float) mViewSize.getWidth() / mVideoSize.getWidth();
			float sy = (float) mViewSize.getHeight() / mVideoSize.getHeight();
			float maxScale = Math.max(sx, sy);
			sx = maxScale / sx;
			sy = maxScale / sy;
			return getMatrix(sx, sy, pivotPoint);
		}

		private Matrix startInside() {
			if (mVideoSize.getHeight() <= mViewSize.getWidth()
					&& mVideoSize.getHeight() <= mViewSize.getHeight()) {
				// video is smaller than view size
				return getOriginalScale(PivotPoint.LEFT_TOP);
			} else {
				// either of width or height of the video is larger than view size
				return fitStart();
			}
		}

		private Matrix centerInside() {
			if (mVideoSize.getHeight() <= mViewSize.getWidth()
					&& mVideoSize.getHeight() <= mViewSize.getHeight()) {
				// video is smaller than view size
				return getOriginalScale(PivotPoint.CENTER);
			} else {
				// either of width or height of the video is larger than view size
				return fitCenter();
			}
		}

		private Matrix endInside() {
			if (mVideoSize.getHeight() <= mViewSize.getWidth()
					&& mVideoSize.getHeight() <= mViewSize.getHeight()) {
				// video is smaller than view size
				return getOriginalScale(PivotPoint.RIGHT_BOTTOM);
			} else {
				// either of width or height of the video is larger than view size
				return fitEnd();
			}
		}
	}
}
