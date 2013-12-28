package name.vbraun.view.write;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;

public class TouchHandlerSelect extends TouchHandlerABC {

	private int penID = -1;
	private float oldX, oldY, newX, newY;  // main pointer (usually pen)
	private final RectF mRectF = new RectF();
	public enum SelectMode {
		MAGICWAND, MOVE
	}
	SelectMode mode;

	protected TouchHandlerSelect(HandwriterView view) {
		super(view);
		mode = SelectMode.MAGICWAND;
	}	

	@Override
	protected void destroy() {
	}

	@Override
	protected boolean onTouchEvent(MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_DOWN && !view.emptySelection())
			mode = SelectMode.MOVE;
		if (mode == SelectMode.MAGICWAND) {
			if (action == MotionEvent.ACTION_MOVE) {
				if (penID == -1) return true;
				int idx = event.findPointerIndex(penID);
				if (idx == -1) return true;
				newX = event.getX(idx);
				newY = event.getY(idx);
				mRectF.set(oldX, oldY, newX, newY);
				mRectF.sort();
				mRectF.inset(-15, -15);
				view.selectStrokesIn(mRectF);
				view.selectLineArtIn(mRectF);
				oldX = newX;
				oldY = newY;
				return true;
			} else if (action == MotionEvent.ACTION_DOWN) {  // start move
				if (view.isOnPalmShield(event)) 
					return true;
				if (!useForWriting(event)) 
					return true;   // eat non-pen events
				penID = event.getPointerId(0);
				oldX = newX = event.getX();
				oldY = newY = event.getY();
				view.startSelectionInCurrentPage();
				return true;
			} else if (action == MotionEvent.ACTION_UP) { 
				penID = -1;
				mode = SelectMode.MOVE;
				view.selectionChanged();
			}
		} else if (mode == SelectMode.MOVE) {
			if (action == MotionEvent.ACTION_DOWN) { // start move
				if (view.isOnPalmShield(event)) 
					return true;
				if (!useForWriting(event)) 
					return true;   // eat non-pen events
				penID = event.getPointerId(0);
				oldX = newX = event.getX();
				oldY = newY = event.getY();
				if (!view.selectionInCurrentPage() || !view.touchesSelection(newX, newY)) {
					view.startSelectionInCurrentPage();
					mode = SelectMode.MAGICWAND;
				}
				return true;
			} else if (action == MotionEvent.ACTION_MOVE) {
				if (penID == -1) return true;
				int idx = event.findPointerIndex(penID);
				if (idx == -1) return true;
				newX = event.getX(idx);
				newY = event.getY(idx);
				view.translateSelection(newX-oldX,newY-oldY);
				oldX = newX;
				oldY = newY;
				return true;				
			} else if (action == MotionEvent.ACTION_UP) { 
				penID = -1;
				view.commitTranslateSelection();
				view.selectionChanged();
			}
		}
		return false;
	}

	@Override
	protected void draw(Canvas canvas, Bitmap bitmap) {
		canvas.drawBitmap(bitmap, 0, 0, null);
		view.drawSelection(canvas);
	}

}
