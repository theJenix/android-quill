package name.vbraun.view.write;

import junit.framework.Assert;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;

public class TouchHandlerSelect extends TouchHandlerABC {

	private int penID = -1;
	private float oldX, oldY, newX, newY;  // main pointer (usually pen)
	private int fingerId1 = -1;
	private int fingerId2 = -1;
	private float oldX1, oldY1, oldX2, oldY2;
	private float newX1, newY1, newX2, newY2;
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
	protected void interrupt() {
		super.interrupt();
		penID = fingerId1 = fingerId2 = -1;
	}

	@Override
	protected boolean onTouchEvent(MotionEvent event) {
		int action = event.getActionMasked();
		if (action == MotionEvent.ACTION_MOVE) {
			if (getMoveGestureWhileWriting() && fingerId1 != -1 && fingerId2 == -1) {
				int idx1 = event.findPointerIndex(fingerId1);
				if (idx1 != -1) {
					oldX1 = newX1 = event.getX(idx1);
					oldY1 = newY1 = event.getY(idx1);
				}
			}
			if (getMoveGestureWhileWriting() && fingerId2 != -1) {
				Assert.assertTrue(fingerId1 != -1);
				int idx1 = event.findPointerIndex(fingerId1);
				int idx2 = event.findPointerIndex(fingerId2);
				if (idx1 == -1 || idx2 == -1) return true;
				newX1 = event.getX(idx1);
				newY1 = event.getY(idx1);
				newX2 = event.getX(idx2);
				newY2 = event.getY(idx2);		
				if (mode == SelectMode.MOVE) {
					//Log.v("TouchHandlerSelect", "ACTION_MOVE old "+mode+" "+fingerId2+" + "+fingerId1+" "+oldX1+" "+oldY1+" "+oldX2+" "+oldY2);
					//Log.v("TouchHandlerSelect", "ACTION_MOVE new "+mode+" "+fingerId2+" + "+fingerId1+" "+newX1+" "+newY1+" "+newX2+" "+newY2);
					Matrix m = new Matrix();
					float oldFingers[] = {oldX1,oldY1,oldX2,oldY2};
					float newFingers[] = {newX1,newY1,newX2,newY2};
					m.setPolyToPoly(oldFingers, 0, newFingers, 0, 2);					
					//Log.v("TouchHandlerSelect", "ACTION_MOVE "+m.toString());
					view.setSelectionMatrix(m);
					m.mapPoints(oldFingers);
					//Log.v("TouchHandlerSelect", "ACTION_MOVE nld "+mode+" "+fingerId2+" + "+fingerId1+" "+oldFingers[0]+" "+oldFingers[1]+" "+oldFingers[2]+" "+oldFingers[3]);					
				}
				view.invalidate();
				return true;
			}
			if (penID == -1) return true;
			int idx = event.findPointerIndex(penID);
			if (idx == -1) return true;
			newX = event.getX(idx);
			newY = event.getY(idx);

			if (mode == SelectMode.MAGICWAND) {
				mRectF.set(oldX, oldY, newX, newY);
				mRectF.sort();
				mRectF.inset(-15, -15);
				view.selectStrokesIn(mRectF);
				view.selectLineArtIn(mRectF);
				view.selectImageIn(mRectF);
			} else if (mode == SelectMode.MOVE) {
				view.translateSelection(newX-oldX,newY-oldY);
			}

			oldX = newX;
			oldY = newY;
			return true;				
		} else if (action == MotionEvent.ACTION_DOWN) {  // start move
			if (!view.emptySelection())
				mode = SelectMode.MOVE;
			if (view.isOnPalmShield(event)) 
				return true;
			if (getMoveGestureWhileWriting() && useForTouch(event) && event.getPointerCount()==1) {
				fingerId1 = event.getPointerId(0); 
				fingerId2 = -1;
				newX1 = oldX1 = event.getX(); 
				newY1 = oldY1 = event.getY();
			}
			if (!useForWriting(event)) 
				return true;   // eat non-pen events
			penID = event.getPointerId(0);
			oldX = newX = event.getX();
			oldY = newY = event.getY();

			if (mode == SelectMode.MAGICWAND) {
				view.startSelectionInCurrentPage();
			} else if (mode == SelectMode.MOVE) {
				if (!view.selectionInCurrentPage() || !view.touchesSelection(newX, newY)) {
					view.startSelectionInCurrentPage();
					mode = SelectMode.MAGICWAND;
				}
			}
			//Log.v("TouchHandlerSelect", "ACTION_DOWN "+mode+" "+fingerId2+" + "+fingerId1+" "+oldX1+" "+oldY1+" "+oldX2+" "+oldY2);
			return true;
		} else if (action == MotionEvent.ACTION_UP) { 
			Assert.assertTrue(event.getPointerCount() == 1);
			int id = event.getPointerId(0);
			if (id == penID) {
				if (mode == SelectMode.MAGICWAND) {
					penID = -1;
					if (!view.emptySelection())
						mode = SelectMode.MOVE;
					view.selectionChanged();
				} else if (mode == SelectMode.MOVE) {
					penID = -1;
					view.commitTranslateSelection();
					view.selectionChanged();
				}
			} else if (getMoveGestureWhileWriting() && 
						(id == fingerId1 || id == fingerId2) &&
						fingerId1 != -1 && fingerId2 != -1) {
				if (mode == SelectMode.MAGICWAND) {	
					Page page = getPage();
					Transformation t = pinchZoomTransform(page.getTransform(), 
							oldX1, newX1, oldX2, newX2, oldY1, newY1, oldY2, newY2);
					page.setTransform(t, view.canvas);
					page.draw(view.canvas);
					view.invalidate();
				} else if (mode == SelectMode.MOVE) {
					Log.v("TouchHandlerSelect", "ACTION_UP "+mode+" "+fingerId2+" + "+fingerId1+" "+oldX1+" "+oldY1+" "+oldX2+" "+oldY2);
					view.commitScaleRotateSelection();
					view.selectionChanged();					
				}
			}
			//Log.v("TouchHandlerSelect", "ACTION_UP "+mode+" "+fingerId2+" + "+fingerId1+" "+oldX1+" "+oldY1+" "+oldX2+" "+oldY2);
			penID = fingerId1 = fingerId2 = -1;
		} else if (action == MotionEvent.ACTION_CANCEL) {
			// e.g. you start with finger and use pen
			// if (event.getPointerId(0) != penID) return true;
			// Log.v("TouchHandlerSelect", "ACTION_CANCEL");
			penID = fingerId1 = fingerId2 = -1;
			if (mode == SelectMode.MAGICWAND)
				getPage().draw(view.canvas);
			else if (mode == SelectMode.MOVE)
				view.setSelectionMatrix(new Matrix());
			view.invalidate();
			return true;
		}
		else if (action == MotionEvent.ACTION_POINTER_DOWN) {  // start move gesture
			if (fingerId1 == -1) return true; // ignore after move finished
			if (fingerId2 != -1) return true; // ignore more than 2 fingers
			int idx2 = event.getActionIndex();
			oldX2 = newX2 = event.getX(idx2);
			oldY2 = newY2 = event.getY(idx2);
			float dx = newX2-newX1;
			float dy = newY2-newY1;
			float distance = FloatMath.sqrt(dx*dx+dy*dy);
			if (distance >= getMoveGestureMinDistance()) {
				fingerId2 = event.getPointerId(idx2);
			}
			Log.v("TouchHandlerSelect", "ACTION_POINTER_DOWN "+mode+" "+fingerId2+" + "+fingerId1+" "+oldX1+" "+oldY1+" "+oldX2+" "+oldY2);
		}
		return false;
	}

	@Override
	protected void draw(Canvas canvas, Bitmap bitmap) {
		if (fingerId2 != -1 && mode == SelectMode.MAGICWAND) {
			drawPinchZoomPreview(canvas, bitmap, oldX1, newX1, oldX2, newX2, oldY1, newY1, oldY2, newY2);
		} else {
			canvas.drawBitmap(bitmap, 0, 0, null);
			view.drawSelection(canvas);
		}
	}

}
