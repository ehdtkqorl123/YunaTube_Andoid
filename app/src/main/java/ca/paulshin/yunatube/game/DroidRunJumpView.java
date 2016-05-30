package ca.paulshin.yunatube.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class DroidRunJumpView extends SurfaceView implements SurfaceHolder.Callback {

	// Game Thread
	public class DroidRunJumpThread extends Thread {

		private SurfaceHolder surfaceHolder;
		boolean run;
		Game game;

		public DroidRunJumpThread(SurfaceHolder surfaceHolder, Game game) {
			run = false;
			this.surfaceHolder = surfaceHolder;
			this.game = game;
		}

		public void setSurfaceSize(int width, int height) {
			synchronized (surfaceHolder) {
				game.setScreenSize(width, height);
			}
		}

		public void setRunning(boolean b) {
			run = b;
		}

		@Override
		public void run() {

			// Game loop
			while (run) {
				Canvas c = null;
				try {
					c = surfaceHolder.lockCanvas(null);
					if (c != null) {
						synchronized (surfaceHolder) {
							game.run(c);
						}
					}
				} finally {
					if (c != null) {
						surfaceHolder.unlockCanvasAndPost(c);
					}
				}
			}
		}

		boolean doTouchEvent(MotionEvent event) {
			boolean handled = false;

			synchronized (surfaceHolder) {
				switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						game.doTouch();
						handled = true;
						break;
				}
			}

			return handled;
		}

		public void pause() {
			synchronized (surfaceHolder) {
				game.pause();
				run = false;
			}
		}

		public void resetGame() {
			synchronized (surfaceHolder) {
				game.resetGame();
			}
		}

		public void restoreGame(SharedPreferences savedInstanceState) {
			synchronized (surfaceHolder) {
				game.restore(savedInstanceState);
			}
		}

		public void saveGame(SharedPreferences.Editor editor) {
			synchronized (surfaceHolder) {
				game.save(editor);
			}
		}
	}

	// Game View
	private DroidRunJumpThread thread;
	private Game game;
	private SurfaceHolder holder;

	public DroidRunJumpView(Context context, AttributeSet attrs) {
		super(context, attrs);

		holder = getHolder();
		holder.addCallback(this);

		game = new Game(context);
		thread = null;

		setFocusable(true);
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
							   int height) {
		thread.setSurfaceSize(width, height);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		thread.setRunning(true);
		thread.start();
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
				setVisibility(GONE);
			}
		}

		thread = null;
	}

	public boolean onTouchEvent(MotionEvent event) {
		return thread.doTouchEvent(event);
	}

	public DroidRunJumpThread getThread() {
		if (thread == null) {
			thread = new DroidRunJumpThread(holder, game);
		}
		return thread;
	}
}
