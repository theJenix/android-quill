package name.vbraun.view.write;

import name.vbraun.view.write.Graphics.Tool;
import name.vbraun.view.write.HandwriterView.SelectMode;
import junit.framework.Assert;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
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
	private Paint pen;
	protected Lasso lasso;
	private int Nmax = 1024;


	protected TouchHandlerSelect(HandwriterView view) {
		super(view);
		view.setSelectMode(SelectMode.SELECT);
		pen = new Paint();
		pen.setAntiAlias(true);
		pen.setARGB(0xff, 0, 0, 0);
		pen.setStyle(Paint.Style.STROKE);
		float[] dash = {5,5}; 
		pen.setPathEffect(new DashPathEffect(dash, 0));
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
		SelectMode mode = view.getSelectMode();
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

			if (mode == SelectMode.SELECT) {
				mRectF.set(oldX, oldY, newX, newY);
				mRectF.sort();
				if (view.getSelectTool() == Tool.SELECT_WAND)
					mRectF.inset(-15, -15);
				if (view.getSelectTool() == Tool.SELECT_FREE && lasso != null) {
					if (mRectF.height()+mRectF.width() > 10 && !lasso.full()) {
						lasso.add(newX, newY);
						drawOutline(oldX,oldY,newX,newY);
						oldX = newX;
						oldY = newY;
					}
				} else
					view.selectIn(mRectF);
				if (view.getSelectTool() == Tool.SELECT_RECT) {
					view.filterSelection(mRectF);
				}
			} else if (mode == SelectMode.MOVE) {
				view.translateSelection(newX-oldX,newY-oldY);
			}
			
			if (mode == SelectMode.MOVE || view.getSelectTool() == Tool.SELECT_WAND) {
				oldX = newX;
				oldY = newY;
			}
			return true;				
		} else if (action == MotionEvent.ACTION_DOWN) {  // start move
			if (!view.emptySelection()) {
				view.setSelectMode(SelectMode.MOVE);
				mode = SelectMode.MOVE;
			}
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
			lasso = new Lasso(newX, newY);

			if (mode == SelectMode.SELECT) {
				view.startSelectionInCurrentPage();
			} else if (mode == SelectMode.MOVE) {
				if (!view.selectionInCurrentPage() || !view.touchesSelection(newX, newY)) {
					view.startSelectionInCurrentPage();
					view.setSelectMode(SelectMode.SELECT);
					mode = SelectMode.SELECT;
				}
			}
			//Log.v("TouchHandlerSelect", "ACTION_DOWN "+mode+" "+fingerId2+" + "+fingerId1+" "+oldX1+" "+oldY1+" "+oldX2+" "+oldY2);
			return true;
		} else if (action == MotionEvent.ACTION_UP) { 
			Assert.assertTrue(event.getPointerCount() == 1);
			int id = event.getPointerId(0);
			if (id == penID) {
				if (mode == SelectMode.SELECT) {
					penID = -1;
					if (view.getSelectTool() == Tool.SELECT_FREE) {
						view.selectIn(lasso);
						lasso = null;
					}
					if (!view.emptySelection()) {
						view.setSelectMode(SelectMode.MOVE);
						mode = SelectMode.MOVE;
					}
					view.selectionChanged();
				} else if (mode == SelectMode.MOVE) {
					penID = -1;
					view.commitTranslateSelection();
					view.selectionChanged();
				}
			} else if (getMoveGestureWhileWriting() && 
						(id == fingerId1 || id == fingerId2) &&
						fingerId1 != -1 && fingerId2 != -1) {
				if (mode == SelectMode.SELECT) {	
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
			if (mode == SelectMode.SELECT)
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
		if (fingerId2 != -1 && view.getSelectMode() == SelectMode.SELECT) {
			drawPinchZoomPreview(canvas, bitmap, oldX1, newX1, oldX2, newX2, oldY1, newY1, oldY2, newY2);
		} else {
			canvas.drawBitmap(bitmap, 0, 0, null);
			view.drawSelection(canvas);
			if (view.getSelectTool() == Tool.SELECT_RECT && 
					mRectF != null && view.selectionInCurrentPage() && penID != -1) {
				canvas.drawRect(mRectF, pen);
				view.invalidate();
			}
			if (view.getSelectTool() == Tool.SELECT_FREE && 
					lasso != null && view.selectionInCurrentPage() && penID != -1) {
				canvas.drawLine(newX, newY, lasso.startX(), lasso.startY(), pen);
				view.invalidate();
			}
		}
	}
	
	protected void drawOutline(float oldX, float oldY, float newX, float newY) {
		view.selectionCanvas.drawLine(oldX, oldY, newX, newY, pen);
		Rect mRect = new Rect();
		mRect.set((int) oldX, (int) oldY, (int) newX, (int) newY);
		mRect.sort();
		mRect.inset(-10, -10);
		view.invalidate(mRect);
	}

}
