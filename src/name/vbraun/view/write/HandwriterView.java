package name.vbraun.view.write;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import com.write.Quill.R;
import com.write.Quill.data.Book;
import com.write.Quill.data.BookDirectory;
import com.write.Quill.data.Bookshelf;
import com.write.Quill.data.Storage;

import name.vbraun.lib.pen.HardwareButtonListener;
import name.vbraun.lib.pen.Hardware;
import name.vbraun.view.write.Graphics.Tool;
import name.vbraun.view.write.LinearFilter.Filter;

import junit.framework.Assert;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.util.Log;
import android.view.Display;
import android.view.DragEvent;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.view.WindowManager;


public class HandwriterView 
	extends ViewGroup 
	implements HardwareButtonListener {
	
	private static final String TAG = "Handwrite";
	
	public static final String KEY_LIST_PEN_INPUT_MODE = "pen_input_mode";
	public static final String KEY_DOUBLE_TAP_WHILE_WRITE = "double_tap_while_write";
	public static final String KEY_MOVE_GESTURE_WHILE_WRITING = "move_gesture_while_writing";
	public static final String KEY_MOVE_GESTURE_FIX_ZOOM = "move_gesture_fix_zoom";
	public static final String KEY_PALM_SHIELD = "palm_shield";
	public static final String KEY_TOOLBOX_IS_ON_LEFT = "toolbox_left";
	private static final String KEY_TOOLBOX_IS_VISIBLE = "toolbox_is_visible";
	private static final String KEY_PEN_TYPE = "pen_type";
	private static final String KEY_PEN_COLOR = "pen_color";
	private static final String KEY_PEN_THICKNESS = "pen_thickness";
	public static final String KEY_DEBUG_OPTIONS = "debug_options_enable";
	public static final String KEY_PEN_SMOOTH_FILTER = "pen_smooth_filter";
	
	// values for the preferences key KEY_LIST_PEN_INPUT_MODE
    public static final String STYLUS_ONLY = "STYLUS_ONLY";
    public static final String STYLUS_WITH_GESTURES = "STYLUS_WITH_GESTURES";
    public static final String STYLUS_AND_TOUCH = "STYLUS_AND_TOUCH";
    
    // values for the preferences key KEY_PEN_SMOOTH_FILTER
    public static final String SMOOTH_FILTER_NONE = "SMOOTH_FILTER_NONE";
    public static final String SMOOTH_FILTER_GAUSSIAN = "SMOOTH_FILTER_GAUSSIAN";
    public static final String SMOOTH_FILTER_GAUSSIAN_HQ = "SMOOTH_FILTER_GAUSSIAN_HQ";
    public static final String SMOOTH_FILTER_SAVITZKY_GOLAY = "SMOOTH_FILTER_SAVITZKY_GOLAY";
    public static final String SMOOTH_FILTER_SAVITZKY_GOLAY_HQ = "SMOOTH_FILTER_SAVITZKY_GOLAY_HQ";
    

    protected final float screenDensity;
	private TouchHandlerABC touchHandler;

	private Bitmap bitmap;
	protected Canvas canvas;
	private Toast toast;
	
	private Tool currentSelectTool = Tool.SELECT_WAND;
	private LinkedList<Stroke> selectedStrokes = new LinkedList<Stroke> ();
	private LinkedList<GraphicsLine> selectedLineArt = new LinkedList<GraphicsLine> ();
	private LinkedList<GraphicsImage> selectedImage = new LinkedList<GraphicsImage> ();
	private Bitmap selectionBitmap;
	protected Canvas selectionCanvas;
	private Matrix selectionMatrix = new Matrix();
	private Page selectionInPage = null;
	private float selectionDX = 0f;
	private float selectionDY = 0f;
	public enum SelectMode {
		SELECT, MOVE
	}
	private SelectMode selectMode = SelectMode.SELECT;
	public SelectMode getSelectMode() {
		return selectMode;
	}
	public void setSelectMode(SelectMode m) {
		selectMode = m;
	}
	
	private boolean palmShield = false;
	private RectF palmShieldRect;
	private Paint palmShieldPaint;
	
	private Toolbox toolbox;
	public Toolbox getToolBox() {
		return toolbox;
	}
	
	private ToolHistory toolHistory = ToolHistory.getToolHistory();
	
	private Overlay overlay = null;
	public void setOverlay(Overlay overlay) {
		this.overlay = overlay;
		invalidate();
	}
	
	private GraphicsModifiedListener graphicsListener = null;
  
	
	private InputListener inputListener = null;
	
	public void setOnInputListener(InputListener listener) {
		inputListener = listener;
	}
	
	protected void callOnStrokeFinishedListener() {
		if (inputListener != null)
			inputListener.onStrokeFinishedListener();
	}
	
	protected void callOnEditImageListener(GraphicsImage image) {
		File file = image.getFile();
		if (inputListener == null)
			return;
		if (file == null)
			inputListener.onPickImageListener(image);
		else
			inputListener.onEditImageListener(image);
	}
	
	protected void callOnSelectionChangedListener() {
		if (inputListener != null)
			inputListener.onSelectionChangedListener();
	}

	// actual data
	private Page page;
	
	// preferences
	private int pen_thickness = -1;
	private int pen_color = -1;
	private Tool tool_type = null;
	private Filter penSmoothFilter = Filter.KERNEL_SAVITZKY_GOLAY_11;
	protected boolean onlyPenInput = true;
	protected boolean moveGestureWhileWriting = true;
	protected boolean moveGestureFixZoom = true;
	protected int moveGestureMinDistance = 400; // pixels
	protected boolean doubleTapWhileWriting = true;
	
	private boolean acceptInput = false;
	
	/**
	 * Stop input event processing (to be called from onPause/onResume if you want to make sure 
	 * that no events are processed
	 */
	public void stopInput() {
		acceptInput = false;
	}
	
	/**
	 * Start input event processing. Needs to be called from onResume() when the 
	 * activity is ready to receive input.
	 */
	public void startInput() {
		acceptInput = true;
	}
	
	public void setOnGraphicsModifiedListener(GraphicsModifiedListener newListener) {
		graphicsListener = newListener;
	}
	
	public void add(Graphics graphics) {
		if (graphics instanceof Stroke) { // most likely first
			Stroke s = (Stroke)graphics;
			page.addStroke(s);
		} else if (graphics instanceof GraphicsLine ) {
			GraphicsLine l = (GraphicsLine)graphics;
			page.addLine(l);
		} else if (graphics instanceof GraphicsImage ) {
			GraphicsImage img = (GraphicsImage)graphics;
			page.addImage(img);
		} else
			Assert.fail("Unknown graphics object");
		page.draw(canvas, graphics.getBoundingBox());
		invalidate(graphics.getBoundingBoxRoundOut());
	}
	
	public void remove(Graphics graphics) {
		if (graphics instanceof Stroke) { 
			Stroke s = (Stroke)graphics;
			page.removeStroke(s);
		} else if (graphics instanceof GraphicsLine ) {
			GraphicsLine l = (GraphicsLine)graphics;
			page.removeLine(l);
		} else if (graphics instanceof GraphicsImage ) {
			GraphicsImage img = (GraphicsImage)graphics;
			page.removeImage(img);
		} else
			Assert.fail("Unknown graphics object");
		page.draw(canvas, graphics.getBoundingBox());
		invalidate(graphics.getBoundingBoxRoundOut());
	}
	
    public void add(LinkedList<Stroke> penStrokes) {
    	getPage().strokes.addAll(penStrokes);
		page.draw(canvas);
    	invalidate();
    }
    
    public void remove(LinkedList<Stroke> penStrokes) {
    	getPage().strokes.removeAll(penStrokes);
		page.draw(canvas);
    	invalidate();
   }
    
    /**
     * Set the image
     * @param uuid The UUID
     * @param name The image file name (path+uuid+extension)
     */
    public void setImage(UUID uuid, String name, boolean constrainAspect) {
    	for (GraphicsImage image : getPage().images)
    		if (image.getUuid().equals(uuid)) {
    			if (name==null)
    				getPage().images.remove(image);
    			else { 
    				if (image.checkFileName(name)) {
        				image.setFile(name, constrainAspect);
    				} else {
    					Log.e(TAG, "incorrect image file name");
        				getPage().images.remove(image);
    				}
    			}
    			page.draw(canvas);
    			invalidate();
    			return;
    		}
    	Log.e(TAG, "setImage(): Image does not exist");
    }
	
    public GraphicsImage getImage(UUID uuid) {
    	for (GraphicsImage image : getPage().images)
    		if (image.getUuid().equals(uuid))
    			return image;
    	Log.e(TAG, "getImage(): Image does not exists");
    	return null;
    }
    
	public void interrupt() {
		if (page==null || canvas==null)
			return;
		Log.d(TAG, "Interrupting current interaction");
		if (touchHandler != null) 
			touchHandler.interrupt();
		page.draw(canvas);
		invalidate();
	}
	
	public void setToolType(Tool tool) {
		if (tool.equals(tool_type)) return;
		if (Graphics.isSelectTool(tool) && Graphics.isSelectTool(tool_type)) {
			toolbox.setActiveTool(tool);
			tool_type = tool;
			return;
		}
		if (touchHandler != null) {
			touchHandler.destroy();
			touchHandler = null;
		}
		clearSelection();
		switch (tool) {
		case FOUNTAINPEN:
		case PENCIL:
			if (onlyPenInput)
				touchHandler = new TouchHandlerActivePen(this);
			else
				touchHandler = new TouchHandlerPassivePen(this);
			toolHistory.setTool(tool);
			break;
		case SELECT_WAND:
		case SELECT_FREE:
		case SELECT_RECT:
			touchHandler = new TouchHandlerSelect(this);
			break;
		case ARROW:
			break;
		case LINE:
			touchHandler = new TouchHandlerLine(this);
			break;
		case MOVE:
			touchHandler = new TouchHandlerMoveZoom(this);
			break;
		case ERASER:
			touchHandler = new TouchHandlerEraser(this);
			break;
		case TEXT:
			touchHandler = new TouchHandlerText(this);
			break;
		case IMAGE:
			touchHandler = new TouchHandlerImage(this);
			break;
		default:
			touchHandler = null;
		}
		toolbox.setActiveTool(tool);
		tool_type = tool;
	}

	public Tool getToolType() {
		return tool_type;
	}

	public Filter getPenSmoothFilter() {
		return penSmoothFilter;
	}
	
	public void setPenSmootFilter(Filter filter) {
		this.penSmoothFilter = filter;
		// Log.e(TAG, "Pen smoothen filter = "+filter);
	}
	
	public int getPenThickness() {
		return pen_thickness;
	}

	public void setPenThickness(int thickness) {
		pen_thickness = thickness;
		toolHistory.setThickness(thickness);
		toolbox.setThickness(thickness);
	}
	
	public int getPenColor() {
		return pen_color;
	}
	
	public void setPenColor(int c) {
		pen_color = c;
		toolHistory.setColor(c);
	}
	
	public Page getPage() {
		return page;
	}
	
	public Paper.Type getPagePaperType() {
		return page.paper_type;
	}
	
	public void setPagePaperType(Paper.Type paper_type) {
		page.setPaperType(paper_type);
		page.draw(canvas);
		invalidate();
	}

	public float getPageAspectRatio() {
		return page.aspect_ratio;
	}
	
	public void setPageAspectRatio(float aspect_ratio) {
		page.setAspectRatio(aspect_ratio);
		setPageAndZoomOut(page);
		invalidate();
	}

	public boolean getOnlyPenInput() {
		return onlyPenInput;
	}

	public void setOnlyPenInput(boolean onlyPenInput) {
		this.onlyPenInput = onlyPenInput;
	}

	public boolean getDoubleTapWhileWriting() {
		return doubleTapWhileWriting;
	}

	public void setDoubleTapWhileWriting(boolean doubleTapWhileWriting) {
		this.doubleTapWhileWriting = doubleTapWhileWriting;
	}

	public boolean getMoveGestureWhileWriting() {
		return moveGestureWhileWriting;
	}

	public void setMoveGestureWhileWriting(boolean moveGestureWhileWriting) {
		this.moveGestureWhileWriting = moveGestureWhileWriting;
	}

	public boolean getMoveGestureFixZoom() {
		return moveGestureFixZoom;
	}
	
	public void setMoveGestureFixZoom(boolean moveGestureFixZoom) {
		this.moveGestureFixZoom = moveGestureFixZoom;
	}
	
	public int getMoveGestureMinDistance() {
		return moveGestureMinDistance;
	}

	public void setMoveGestureMinDistance(int moveGestureMinDistance) {
		this.moveGestureMinDistance = moveGestureMinDistance;
	}

	public void setPalmShieldEnabled(boolean enabled) {
		palmShield = enabled;
		initPalmShield();
		invalidate();
	}
	
	private void initPalmShield() {
		if (!palmShield) return;
		if (toolboxIsOnLeft)  // for right-handed user
			palmShieldRect = new RectF(0, getHeight()/2, getWidth(), getHeight());
		else  // for left-handed user
			palmShieldRect = new RectF(0, 0, getWidth(), getHeight()/2);
		palmShieldPaint = new Paint();
		palmShieldPaint.setARGB(0x22, 0, 0, 0);		
	}
	
	/**
	 * Whether the point (x,y) is on the palm shield and hence should be ignored.
	 * @param event
	 * @return whether the touch point is to be ignored.
	 */
	protected boolean isOnPalmShield(MotionEvent event) {
		if (!palmShield)
			return false;
		int action = event.getActionMasked();
		switch (action) {
		case MotionEvent.ACTION_DOWN:
			return palmShieldRect.contains(event.getX(), event.getY());
		case MotionEvent.ACTION_POINTER_DOWN:
			int idx = event.getActionIndex();
			return palmShieldRect.contains(event.getX(idx), event.getY(idx));
		}
		return false;
	}
	
	public HandwriterView(Context context) {
		super(context);
		setFocusable(true);
		setAlwaysDrawnWithCacheEnabled(false);
		setDrawingCacheEnabled(false);
		setWillNotDraw(false);
		setBackgroundDrawable(null);
		
		Display display = ((WindowManager) 
        		context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        screenDensity = metrics.density;

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
    	boolean left = settings.getBoolean(KEY_TOOLBOX_IS_ON_LEFT, true);
    	setToolbox(left);
    	
    	Hardware hw = Hardware.getInstance(context);
    	hw.addViewHack(this);
    	hw.setOnHardwareButtonListener(this);
    	// setLayerType(LAYER_TYPE_SOFTWARE, null);
	}	

	/**
	 * To be called from the onResume method of the activity. Update appearance according to preferences etc.
	 */
	public void loadSettings(SharedPreferences settings) {
    	boolean toolbox_left = settings.getBoolean(KEY_TOOLBOX_IS_ON_LEFT, true);
    	setToolbox(toolbox_left);

    	int toolTypeInt = settings.getInt(KEY_PEN_TYPE, Tool.FOUNTAINPEN.ordinal());
    	Stroke.Tool toolType = Stroke.Tool.values()[toolTypeInt];
    	if (toolType == Tool.ERASER)  // don't start with sharp whirling blades 
    		toolType = Tool.MOVE;
    	setToolType(toolType);

    	boolean toolbox_is_visible = settings.getBoolean(KEY_TOOLBOX_IS_VISIBLE, false);
        getToolBox().setToolboxVisible(toolbox_is_visible);
    	setMoveGestureMinDistance(settings.getInt("move_gesture_min_distance", 400));
       	
    	int penColor = settings.getInt(KEY_PEN_COLOR, Color.BLACK);
    	int penThickness = settings.getInt(KEY_PEN_THICKNESS, 2);
    	setPenColor(penColor);
    	setPenThickness(penThickness);
    	
    	ToolHistory history = ToolHistory.getToolHistory();
    	history.restoreFromSettings(settings);
    	getToolBox().onToolHistoryChanged(false);
    	
    	final boolean hwPen = Hardware.hasPenDigitizer();
        String pen_input_mode;
		if (hwPen)
			pen_input_mode = settings.getString(KEY_LIST_PEN_INPUT_MODE, STYLUS_WITH_GESTURES);
		else
			pen_input_mode = STYLUS_AND_TOUCH;
		Log.d(TAG, "pen input mode "+pen_input_mode);
		if (pen_input_mode.equals(STYLUS_ONLY)) {
			setOnlyPenInput(true);
			setDoubleTapWhileWriting(false);
			setMoveGestureWhileWriting(false);
			setMoveGestureFixZoom(false);
			setPalmShieldEnabled(false);
		}
		else if (pen_input_mode.equals(STYLUS_WITH_GESTURES)) {
			setOnlyPenInput(true);
			setDoubleTapWhileWriting(settings.getBoolean(
					KEY_DOUBLE_TAP_WHILE_WRITE, hwPen));
    		setMoveGestureWhileWriting(settings.getBoolean(
    				KEY_MOVE_GESTURE_WHILE_WRITING, hwPen));
			setMoveGestureFixZoom(settings.getBoolean(KEY_MOVE_GESTURE_FIX_ZOOM, false));
    		setPalmShieldEnabled(false);
		}
		else if (pen_input_mode.equals(STYLUS_AND_TOUCH)) {
			setOnlyPenInput(false);
			setDoubleTapWhileWriting(false);
			setMoveGestureWhileWriting(false);
			setMoveGestureFixZoom(false);
			setPalmShieldEnabled(settings.getBoolean(KEY_PALM_SHIELD, false));
		}
		else Assert.fail();
	
//		final String pen_smooth_filter = settings.getString
//				(KEY_PEN_SMOOTH_FILTER, Filter.KERNEL_SAVITZKY_GOLAY_11.toString());
		final String pen_smooth_filter = settings.getString(KEY_PEN_SMOOTH_FILTER, 
				getContext().getString(R.string.preferences_pen_smooth_default));
		setPenSmootFilter(Filter.valueOf(pen_smooth_filter));

	}
	
	
	/**
	 * To be called from the onPause method of the activity. Save preferences etc.
	 * Note: Settings that can only be changed in preferences need not be saved, they
	 * are saved by the preferences.
	 */
	public void saveSettings(SharedPreferences.Editor editor) {    
    	editor.putBoolean(KEY_TOOLBOX_IS_VISIBLE, getToolBox().isToolboxVisible());
        editor.putInt(KEY_PEN_TYPE, getToolType().ordinal());
        editor.putInt(KEY_PEN_COLOR, getPenColor());
        editor.putInt(KEY_PEN_THICKNESS, getPenThickness());

		ToolHistory history = ToolHistory.getToolHistory();
    	history.saveToSettings(editor);
	}
	
	
	private boolean toolboxIsOnLeft;
	
	public boolean isToolboxOnLeft() {
		return toolboxIsOnLeft;
	}
	
	public void setToolbox(boolean left) {
		if (toolbox != null && toolboxIsOnLeft == left) return;
		if (toolbox != null) 
			removeView(toolbox);
		toolbox = new Toolbox(getContext(), left);
		addView(toolbox);
		toolboxIsOnLeft = left;
	}
	
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		for (int i=0; i<getChildCount(); i++)
			getChildAt(i).layout(l, t, r, b);
//		toolbox.layout(l, t, r, b);
//		if (editText != null) {
//			editText.layout(100, 70, 400, 200);
//		}
		if (palmShield) 
			initPalmShield();
 	}
	
	public void setOnToolboxListener(Toolbox.OnToolboxListener listener) {
		toolbox.setOnToolboxListener(listener);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		toolbox.measure(widthMeasureSpec, heightMeasureSpec);
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
	
	public void setPageAndZoomOut(Page new_page) {
		if (new_page == null) return;
		page = new_page;
		if (canvas == null) return;
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
			zoomFitWidth();
		else
			zoomOutOverview();
		page.draw(canvas);
		invalidate();
	}
	
	private void zoomOutOverview() {
		float H = canvas.getHeight();
		float W = canvas.getWidth();
		float dimension = Math.min(H, W/page.aspect_ratio);
		float h = dimension; 
		float w = dimension*page.aspect_ratio;
		if (h<H)
			page.setTransform(0, (H-h)/2, dimension);
		else if (w<W)
			page.setTransform((W-w)/2, 0, dimension);
		else
			page.setTransform(0, 0, dimension);
	}
	
	private void zoomFitWidth() {
		float H = canvas.getHeight();
		float W = canvas.getWidth();
		float dimension = W/page.aspect_ratio;
		float w = dimension*page.aspect_ratio;
		float offset_y;
		RectF r = page.getLastStrokeRect();
		if (r == null)
			offset_y = 0;
		else {
			float y_center = r.centerY() * dimension;
			float screen_h = w/W*H;
			offset_y = screen_h/2 - y_center;  // put y_center at screen center
			if (offset_y > 0) offset_y = 0;
			if (offset_y - screen_h < -dimension) offset_y = -dimension + screen_h;
		}
		page.setTransform(0, offset_y, dimension);
	}
	
	protected void centerAndFillScreen(float xCenter, float yCenter) {
		float page_offset_x = page.transformation.offset_x;
		float page_offset_y = page.transformation.offset_y;
		float page_scale = page.transformation.scale;
		float W = canvas.getWidth();
		float H = canvas.getHeight();
		float scaleToFill = Math.max(H, W / page.aspect_ratio);
		float scaleToSeeAll = Math.min(H, W / page.aspect_ratio);
		float scale;
		boolean seeAll = (page_scale == scaleToFill); // toggle
		if (seeAll) 
			scale = scaleToSeeAll;
		else
			scale = scaleToFill;
		float x = (xCenter - page_offset_x) / page_scale * scale;
		float y = (yCenter - page_offset_y) / page_scale * scale;
		float dx, dy;
		if (seeAll) {
			dx = (W-scale*page.aspect_ratio)/2;
			dy = (H-scale)/2;
		} else if (scale == H) {
			dx = W/2-x;// + (-scale*page.aspect_ratio)/2;
			dy = 0;
		} else {
			dx = 0;
			dy = H/2-y;// + (-scale)/2;
		}
		page.setTransform(dx, dy, scale, canvas);
		page.draw(canvas);
		invalidate();
	}

	
	public void clear() {
		graphicsListener.onPageClearListener(page);
		page.draw(canvas);
		invalidate();
	}
	
	@Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		int curW = bitmap != null ? bitmap.getWidth() : 0;
		int curH = bitmap != null ? bitmap.getHeight() : 0;
		if (curW >= w && curH >= h) {
			return;
		}
		if (curW < w) curW = w;
		if (curH < h) curH = h;

		Bitmap newBitmap = Bitmap.createBitmap(curW, curH,
				Bitmap.Config.RGB_565);
		Canvas newCanvas = new Canvas();
		newCanvas.setBitmap(newBitmap);
		if (bitmap != null) {
			newCanvas.drawBitmap(bitmap, 0, 0, null);
		}
		bitmap = newBitmap;
		canvas = newCanvas;
		setPageAndZoomOut(page);
	}

	@Override 
	protected void onDraw(Canvas canvas) {
		if (bitmap == null) return;
		if (touchHandler != null) 
			touchHandler.draw(canvas, bitmap);
		if (overlay != null) 
			overlay.draw(canvas);
		if (palmShield) {
			canvas.drawRect(palmShieldRect, palmShieldPaint);
		}
	}

	@Override 
	public boolean onTouchEvent(MotionEvent event) {
		if (!acceptInput) return false;
		if (touchHandler == null) return false;
		
		// Log.e(TAG, "onTouch "+ Hardware.isPenButtonPressed(event));
		// switch to eraser if button is pressed
		if (getToolType() != Tool.ERASER && Hardware.isPenButtonPressed(event)) {
			interrupt();
			setToolType(Tool.ERASER);
		}
		
		// return touchHandler.onTouchEvent(event);
		touchHandler.onTouchEvent(event);
		return true;
	}
	
	@Override
	public void onHardwareButtonListener(Type button) {
		interrupt();
		setToolType(Tool.ERASER);
	}
	
	protected void toastIsReadonly() {
		String s = "Page is readonly";
	   	if (toast == null)
        	toast = Toast.makeText(getContext(), s, Toast.LENGTH_SHORT);
    	else {
    		toast.setText(s);
    	}
	   	toast.show();
	}

	public boolean eraseStrokesIn(RectF r) {
		LinkedList<Stroke> toRemove = new LinkedList<Stroke>();
	    for (Stroke s: page.strokes) {	
			if (!RectF.intersects(r, s.getBoundingBox())) continue;
			if (s.intersects(r)) {
				toRemove.add(s);
			}
		}
	    for (Stroke s : toRemove)
	    	graphicsListener.onGraphicsEraseListener(page, s);
		if (toRemove.isEmpty())
			return false;
		else {
			invalidate();
			return true;
		}
	}

	public boolean selectIn(Lasso lasso) {
		startSelectionInCurrentPage();
		boolean ping = false;
		for(Stroke s: page.strokes) 
			if (s.intersects(lasso)) {
				addStrokeToSelection(s);
				ping = true;
			}
		for(GraphicsLine s: page.lineArt) 
			if (s.intersects(lasso)) {
				addLineArtToSelection(s);
				ping = true;
			}
		for(GraphicsImage s: page.images) 
			if (s.intersects(lasso)) {
				addImageToSelection(s);
				ping = true;
			}
		
		if (ping){
			invalidate();
		}
		return ping;
	}
	
	public void selectIn(RectF r) {
		selectStrokesIn(r);
		selectLineArtIn(r);
		selectImageIn(r);		
	}
	
	public void filterSelection(RectF r) {
		if (checkSelection(r)) return;
		clearSelection();
		selectIn(r);
		selectionChanged();
	}

		public boolean checkSelection(RectF r) {
		for (Stroke s: selectedStrokes)
			if (!RectF.intersects(r, s.getBoundingBox())) return false;
		for (GraphicsLine s: selectedLineArt)
			if (!RectF.intersects(r, s.getBoundingBox())) return false;
		for (GraphicsImage s: selectedImage)
			if (!RectF.intersects(r, s.getBoundingBox())) return false;
		for (Stroke s: selectedStrokes)
			if (!s.intersects(r)) return false;
		for (GraphicsLine s: selectedLineArt)
			if (!s.intersects(r)) return false;
		for (GraphicsImage s: selectedImage)
			if (!s.intersects(r)) return false;
		return true;
	}
	
	public boolean selectStrokesIn(RectF r) {
		boolean ping = false;
	    for (Stroke s: page.strokes) {	
			if (!RectF.intersects(r, s.getBoundingBox())) continue;
			if (s.intersects(r)) {
				addStrokeToSelection(s);
				ping = true;
			}
		}
		if (ping){
			invalidate();
		}
		return ping;
	}
	
	public void addStrokeToSelection (Stroke s) {
		if(!selectedStrokes.contains(s)){
			selectedStrokes.add(s);
			Stroke sh = new Stroke(s);
			sh.halofy();
			sh.draw(selectionCanvas, sh.getBoundingBox());
			s.draw(selectionCanvas, s.getBoundingBox());
		}
	}
	
	public void addToSelection(Graphics graphics) {
		if (graphics instanceof Stroke) { // most likely first
			Stroke s = (Stroke)graphics;
			addStrokeToSelection(s);
		} else if (graphics instanceof GraphicsLine ) {
			GraphicsLine l = (GraphicsLine)graphics;
			addLineArtToSelection(l);
		} else
			Assert.fail("Unselectable graphics object");
	}

	public void addToSelection(LinkedList<Graphics> gl) {
		for (Graphics g : gl)
			addToSelection(g);
	}
	
	public void clearSelection(){
		Log.d("HandWriterView","ClearSelection!");
		setSelectMode(SelectMode.SELECT);
		if(emptySelection()) return;
		selectedStrokes = new LinkedList<Stroke> ();
		selectedLineArt = new LinkedList<GraphicsLine> ();
		selectedImage = new LinkedList<GraphicsImage> ();
		//selectionBitmap = null;
		//selectionCanvas = null;
		selectionDX = 0f;
		selectionDY = 0f;
		selectionMatrix.reset();
		invalidate();
		callOnSelectionChangedListener();
	}
	
	public void setSelectTool (Tool tool) {
		if (tool == Tool.SELECT_FREE || tool == Tool.SELECT_RECT || tool == Tool.SELECT_WAND)
			currentSelectTool = tool;
	}
	
	public Tool getSelectTool() {
		return currentSelectTool;
	}
	
	public boolean selectionInCurrentPage() {
		return selectionInPage == getPage();
	}
	
	public void startSelectionInCurrentPage() {
		clearSelection();
		selectionInPage = getPage();
		selectionBitmap = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);
		selectionCanvas = new Canvas(selectionBitmap);
		selectionMatrix.reset();
	}
	
	public void changeSelectionColor(int c) {
		if (emptySelection()) return;
		LinkedList<Stroke> newSelectedStrokes = new LinkedList<Stroke> ();
		for (Stroke s: selectedStrokes) {
			Stroke sc = new Stroke(s);
			sc.setPenColor(c);
			newSelectedStrokes.add(sc);
		}

		LinkedList<GraphicsLine> newSelectedLineArt = new LinkedList<GraphicsLine> ();
		for (GraphicsLine s: selectedLineArt) {
			GraphicsLine sc = new GraphicsLine(s);
			sc.setPenColor(c);
			newSelectedLineArt.add(sc);
		}

		LinkedList<Graphics> gOld = new LinkedList<Graphics> (selectedStrokes);
		gOld.addAll(selectedLineArt);
		LinkedList<Graphics> gNew = new LinkedList<Graphics> (newSelectedStrokes);
		gNew.addAll(newSelectedLineArt);
		graphicsListener.onGraphicsModifyListener(selectionInPage, gOld,gNew);
    	
		selectedStrokes = newSelectedStrokes;    	
    	selectedLineArt = newSelectedLineArt;
    	selectionChanged();
    	
		invalidate();
	}
	
	public void changeSelectionThickness(int t) {
		if (emptySelection()) return;
		LinkedList<Stroke> newSelectedStrokes = new LinkedList<Stroke> ();
		for (Stroke s: selectedStrokes) {
			Stroke sc = new Stroke(s);
			sc.setPenThickness(t);
			newSelectedStrokes.add(sc);
		}

		LinkedList<GraphicsLine> newSelectedLineArt = new LinkedList<GraphicsLine> ();
		for (GraphicsLine s: selectedLineArt) {
			GraphicsLine sc = new GraphicsLine(s);
			sc.setPenThickness(t);
			newSelectedLineArt.add(sc);
		}

		LinkedList<Graphics> gOld = new LinkedList<Graphics> (selectedStrokes);
		gOld.addAll(selectedLineArt);
		LinkedList<Graphics> gNew = new LinkedList<Graphics> (newSelectedStrokes);
		gNew.addAll(newSelectedLineArt);
		graphicsListener.onGraphicsModifyListener(selectionInPage, gOld,gNew);
    	
		selectedStrokes = newSelectedStrokes;    	
    	selectedLineArt = newSelectedLineArt;
    	selectionChanged();
    	
		invalidate();
	}
	
	public void translateSelection(float dx, float dy) {
		if (emptySelection()) return;
		selectionDX += dx;
		selectionDY += dy;
		selectionMatrix.setTranslate(selectionDX, selectionDY);
		invalidate();
	}
	
	public void commitTranslateSelection() {
		commitScaleRotateSelection();
		/* if (emptySelection()) return;
		LinkedList<Stroke> newSelectedStrokes = new LinkedList<Stroke> ();
		for (Stroke s: selectedStrokes) {
			Stroke sc = new Stroke(s);
			sc.translate(selectionDX,selectionDY);
			newSelectedStrokes.add(sc);
		}

		LinkedList<GraphicsLine> newSelectedLineArt = new LinkedList<GraphicsLine> ();
		for (GraphicsLine s: selectedLineArt) {
			GraphicsLine sc = new GraphicsLine(s);
			sc.translate(selectionDX,selectionDY);
			newSelectedLineArt.add(sc);
		}

		LinkedList<Graphics> gOld = new LinkedList<Graphics> (selectedStrokes);
		gOld.addAll(selectedLineArt);
		LinkedList<Graphics> gNew = new LinkedList<Graphics> (newSelectedStrokes);
		gNew.addAll(newSelectedLineArt);
		graphicsListener.onGraphicsModifyListener(selectionInPage, gOld,gNew);

    	selectedStrokes = newSelectedStrokes;
		selectedLineArt = newSelectedLineArt;
		selectionDX = 0f;
		selectionDY = 0f;
		selectionMatrix.reset();
		selectionChanged();
		invalidate();		*/
	}
	
	public void commitScaleRotateSelection() {
		if (emptySelection()) return;
		LinkedList<Stroke> newSelectedStrokes = new LinkedList<Stroke> ();
		for (Stroke s: selectedStrokes) {
			Stroke sc = new Stroke(s);
			sc.applyMatrix(selectionMatrix);
			newSelectedStrokes.add(sc);
		}

		LinkedList<GraphicsLine> newSelectedLineArt = new LinkedList<GraphicsLine> ();
		for (GraphicsLine s: selectedLineArt) {
			GraphicsLine sc = new GraphicsLine(s);
			sc.applyMatrix(selectionMatrix);
			newSelectedLineArt.add(sc);
		}

		LinkedList<GraphicsImage> newSelectedImage = new LinkedList<GraphicsImage> ();
		for (GraphicsImage s: selectedImage) {
			GraphicsImage sc = new GraphicsImage(s);
			sc.applyMatrix(selectionMatrix);
			newSelectedImage.add(sc);
		}

		LinkedList<Graphics> gOld = new LinkedList<Graphics> (selectedStrokes);
		gOld.addAll(selectedLineArt);
		gOld.addAll(selectedImage);
		LinkedList<Graphics> gNew = new LinkedList<Graphics> (newSelectedStrokes);
		gNew.addAll(newSelectedLineArt);
		gNew.addAll(newSelectedImage);
		graphicsListener.onGraphicsModifyListener(selectionInPage, gOld,gNew);

    	selectedStrokes = newSelectedStrokes;
		selectedLineArt = newSelectedLineArt;
		selectedImage = newSelectedImage;
		selectionDX = 0f;
		selectionDY = 0f;
		selectionMatrix.reset();
		selectionChanged();
		invalidate();		
	}
	
	public void eraseSelection() {
		if (emptySelection()) return;

		LinkedList<Graphics> gOld = new LinkedList<Graphics> (selectedStrokes);
		gOld.addAll(selectedLineArt);
		gOld.addAll(selectedImage);
		LinkedList<Graphics> gNew = new LinkedList<Graphics> ();
		graphicsListener.onGraphicsModifyListener(selectionInPage, gOld,gNew);

		clearSelection();
		invalidate();		
	}

	public boolean emptySelection() {
		return (selectedStrokes.size() + selectedLineArt.size() + selectedImage.size() == 0);		
	}

	private static final String AUTHORITY = "com.write.Quill";
	private static final String CONTENT_URI = "content://"+AUTHORITY;
	private static final Uri copyUri = Uri.parse(CONTENT_URI + "/copy");		
	private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	private static final int STROKE = 1;
	private static final int LINEART = 2;
	private static final int IMAGE = 3;
	static {
	    sURIMatcher.addURI(AUTHORITY, "stroke/*", STROKE);
	    sURIMatcher.addURI(AUTHORITY, "lineart/*", LINEART);
	    sURIMatcher.addURI(AUTHORITY, "image/*/*", IMAGE);
	}

	public void copySelection(Context ctx) {
		ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newUri(ctx.getContentResolver(),"URI",copyUri);
		for (Stroke s: selectedStrokes) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream ();
			DataOutputStream dos = new DataOutputStream(baos);
				try {
					s.writeToStream(dos);
					Uri u = Uri.parse(CONTENT_URI + "/stroke/"+Base64.encodeToString(baos.toByteArray(),Base64.URL_SAFE));
					clip.addItem(new ClipData.Item(u));
				} catch (IOException e) {
					Log.e(TAG, "stroke/"+e.toString());
				}
		}
		for (GraphicsLine g: selectedLineArt) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream ();
			DataOutputStream dos = new DataOutputStream(baos);
				try {
					g.writeToStream(dos);
					Uri u = Uri.parse(CONTENT_URI + "/lineart/"+Base64.encodeToString(baos.toByteArray(),Base64.URL_SAFE));
					clip.addItem(new ClipData.Item(u));
				} catch (IOException e) {
					Log.e(TAG, "lineart/"+e.toString());
				}
		}
		for (GraphicsImage g: selectedImage) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream ();
			DataOutputStream dos = new DataOutputStream(baos);
				try {
					g.writeToStream(dos);
					Uri u = Uri.parse(CONTENT_URI + "/image/"+
								Base64.encodeToString(g.getDir().getBytes("UTF-8"),Base64.URL_SAFE)+"/"+
								Base64.encodeToString(baos.toByteArray(),Base64.URL_SAFE));
					clip.addItem(new ClipData.Item(u));
				} catch (IOException e) {
					Log.e(TAG, "image/"+e.toString());
				}
		}
		//Log.v(TAG, "The clip: "+clip.toString());
		clipboard.setPrimaryClip(clip);
	}
	
	public void pasteSelection(Context ctx) {
		ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = clipboard.getPrimaryClip();
		if (clip == null) return;
		if (!clip.getItemAt(0).getUri().equals(copyUri)) return;
		int n = clip.getItemCount();
		if (n <= 1) return;
		Log.d("HandWriterView","pasting, n>1");
		LinkedList<Stroke> pastedStrokes = new LinkedList<Stroke> ();
		LinkedList<GraphicsLine> pastedLineArt = new LinkedList<GraphicsLine> ();
		LinkedList<GraphicsImage> pastedImage = new LinkedList<GraphicsImage> ();
		Storage storage = Storage.getInstance();
		File thisBookDir = null;
		Book book = Bookshelf.getCurrentBook();
        if (book != null)
        	thisBookDir = storage.getBookDirectory(book.getUUID());

		for (int i=1; i<n; i++) {
			ClipData.Item it = clip.getItemAt(i);
			Log.d("HandWriterView","uri: "+it.getUri().toString());			
			String b64 = it.getUri().getLastPathSegment();
			ByteArrayInputStream bais = new ByteArrayInputStream(Base64.decode(b64, Base64.URL_SAFE));
			DataInputStream dis = new DataInputStream(bais);
			try {
				switch (sURIMatcher.match(it.getUri())) {
				case STROKE:
					pastedStrokes.add(new Stroke(dis));
					break;
				case LINEART: 
					pastedLineArt.add(new GraphicsLine(dis));
					break;
				case IMAGE:
					List<String> lst = it.getUri().getPathSegments();
					if (lst.size() != 3)
						throw new IOException("Wrong number of segments in Uri");
					File imgDir = null;
					if (thisBookDir != null) {
						imgDir = new File(new String(Base64.decode(lst.get(1), Base64.URL_SAFE), "UTF-8"));
					}
					GraphicsImage gi = new GraphicsImage(dis,imgDir);
					pastedImage.add(new GraphicsImage(gi, thisBookDir));
					break;
				} 
			}catch (IOException e) {Log.d("HandWriterView",e.toString());}
		}
		LinkedList<Graphics> gNew = new LinkedList<Graphics> (pastedStrokes);
		gNew.addAll(pastedLineArt);
		gNew.addAll(pastedImage);
		LinkedList<Graphics> gOld = new LinkedList<Graphics> ();
		graphicsListener.onGraphicsModifyListener(page, gOld,gNew);
		startSelectionInCurrentPage();
		for (Stroke s: pastedStrokes)
			addStrokeToSelection(s);
		for (GraphicsLine g: pastedLineArt)
			addLineArtToSelection(g);
		for (GraphicsImage g: pastedImage)
			addImageToSelection(g);
		if (!emptySelection())
			setSelectMode(SelectMode.MOVE);
		selectionChanged();
		invalidate();
	}
	
	public void drawSelection(Canvas canvas) {
		if (emptySelection() && currentSelectTool != Tool.SELECT_FREE) return;
		if (!selectionInCurrentPage()) return;
		canvas.drawBitmap(selectionBitmap, selectionMatrix, null);
	}
	
	public void setSelectionMatrix(Matrix m) {
			selectionMatrix = m;
	}

	private void renderSelection() {
		selectionBitmap = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);
		selectionCanvas = new Canvas(selectionBitmap);
		RectF r = new RectF(); 
		r.set(0,0,selectionCanvas.getWidth(), selectionCanvas.getHeight());
		for (GraphicsImage s: selectedImage) {
			GraphicsImage sh = new GraphicsImage(s);
			sh.halofy();
			sh.draw(selectionCanvas, r);
		}
		for (Stroke s: selectedStrokes) {
			Stroke sh = new Stroke(s);
			sh.halofy();
			sh.draw(selectionCanvas, r);
		}
		for (GraphicsLine s: selectedLineArt) {
			GraphicsLine sh = new GraphicsLine(s);
			sh.halofy();
			sh.draw(selectionCanvas, r);
		}
		for (Stroke s: selectedStrokes) {
			s.draw(selectionCanvas, r);
		}
		for (GraphicsLine s: selectedLineArt) {
			s.draw(selectionCanvas, r);
		}
	}
	
	protected void selectionChanged() {
		renderSelection();
		callOnSelectionChangedListener();
	}
	
	public boolean touchesSelection(float x, float y) {
		if (emptySelection()) return false;
		RectF r = new RectF(); 
		r.set(x-15f,y-15f,x+15f, y+15f);
		for (Stroke s: selectedStrokes) {
			if (s.intersects(r))
				return true;
		}		
		for (GraphicsLine s: selectedLineArt) {
			if (s.intersects(r))
				return true;
		}		
		for (GraphicsImage s: selectedImage) {
			if (s.intersects(r))
				return true;
		}		
		return false;
	}
	
	public boolean eraseLineArtIn(RectF r) {
		LinkedList<GraphicsControlpoint> toRemove = new LinkedList<GraphicsControlpoint>();
	    for (GraphicsControlpoint graphics: page.lineArt) {	
			if (!RectF.intersects(r, graphics.getBoundingBox())) continue;
			if (graphics.intersects(r)) {
				toRemove.add(graphics);
			}
		}
	    for (GraphicsControlpoint graphics : toRemove)
	    	graphicsListener.onGraphicsEraseListener(page, graphics);
		if (toRemove.isEmpty())
			return false;
		else {
			invalidate();
			return true;
		}
	}
	
	public boolean selectLineArtIn(RectF r) {
		boolean ping = false;
	    for (GraphicsLine s: page.lineArt) {	
			if (!RectF.intersects(r, s.getBoundingBox())) continue;
			if (s.intersects(r)) {
				addLineArtToSelection(s);
				ping = true;
			}
		}
		if (ping){
			invalidate();
		}
		return ping;
	}

	public void addLineArtToSelection(GraphicsLine g) {
		if (!selectedLineArt.contains(g)) {
			selectedLineArt.add(g);
			GraphicsLine sh = new GraphicsLine(g);
			sh.halofy();
			sh.draw(selectionCanvas, sh.getBoundingBox());
			g.draw(selectionCanvas, g.getBoundingBox());
		}
	}

	public boolean selectImageIn(RectF r) {
		boolean ping = false;
	    for (GraphicsImage s: page.images) {	
			if (!RectF.intersects(r, s.getBoundingBox())) continue;
			if (s.intersects(r)) {
				addImageToSelection(s);
				ping = true;
			}
		}
		if (ping){
			invalidate();
		}
		return ping;
	}

	public void addImageToSelection(GraphicsImage g) {
		if (!selectedImage.contains(g)) {
			selectedImage.add(g);
			GraphicsImage sh = new GraphicsImage(g);
			sh.halofy();
			sh.draw(selectionCanvas, sh.getBoundingBox());
			//g.draw(selectionCanvas, g.getBoundingBox());
		}
	}

	protected void saveStroke(Stroke s) {
		if (page.is_readonly) {
			toastIsReadonly();
			return;
		}
		toolHistory.commit();
		if (page != null && graphicsListener != null) {
			graphicsListener.onGraphicsCreateListener(page, s);
		}
	}
	
	protected void saveGraphics(GraphicsControlpoint graphics) {
		if (page.is_readonly) {
			toastIsReadonly();
			return;
		}
		if (page != null && graphicsListener != null) {
			graphicsListener.onGraphicsCreateListener(page, graphics);
		}
	}
	
	protected void removeGraphics(GraphicsControlpoint graphics) {
		if (page.is_readonly) {
			toastIsReadonly();
			return;
		}
		if (page != null && graphicsListener != null) {
			graphicsListener.onGraphicsEraseListener(page, graphics);
		}
	}
	
	protected void modifyGraphics(GraphicsControlpoint toRemove, GraphicsControlpoint toAdd) {
		if (page.is_readonly) {
			toastIsReadonly();
			return;
		}
		if (page != null && graphicsListener != null) {
			graphicsListener.onGraphicsModifyListener(page, toRemove, toAdd);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (Hardware.onKeyDown(keyCode, event))	
			return true;
		else
			return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (Hardware.onKeyUp(keyCode, event))	
			return true;
		else
			return super.onKeyUp(keyCode, event);
	}

	
	
	@Override
	public boolean onDragEvent(DragEvent event) {
		Log.e(TAG, "onDragEv");
		return super.onDragEvent(event);
	}
	
	
}
