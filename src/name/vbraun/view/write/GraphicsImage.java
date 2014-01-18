package name.vbraun.view.write;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedList;
import java.util.UUID;

import name.vbraun.view.write.GraphicsControlpoint.Controlpoint;

import com.write.Quill.artist.Artist;

import junit.framework.Assert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.util.FloatMath;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

public class GraphicsImage extends GraphicsControlpoint {
	private static final String TAG = "GraphicsImage";

	private Controlpoint bottom_left, bottom_right, top_left, top_right,
			center;
	private final Paint paint = new Paint();
	private final Paint outline = new Paint();
	private final Rect rect = new Rect();
	private final RectF rectF = new RectF();

	private Bitmap bitmap = null;
	private File file = null;
	private Matrix matrix = new Matrix();
	private boolean halo = false;

	private int height, width;
	private float sqrtAspect;

	public enum FileType {
		FILETYPE_NONE, FILETYPE_PNG, FILETYPE_JPG
	}
	
	public static String getImageFileExt(FileType fileType) {
		if (fileType == FileType.FILETYPE_JPG) {
			return ".jpg";
		} else if (fileType == FileType.FILETYPE_PNG) {
			return ".png";
		} else {
			Assert.fail();
			return null;
		}
	}
	
	public static FileType getImageFileType(String fileName) {
		for (FileType t : FileType.values()) {
			if (t == FileType.FILETYPE_NONE)
				continue;
			String ext = getImageFileExt(t);
			if (fileName.endsWith(ext))
				return t;
		}
		return FileType.FILETYPE_NONE;
	}

	/**
	 * Helper to construct a file name out of uuid and file type
	 * @param uuid
	 * @param fileType
	 * @return
	 */
	public static String getImageFileName(UUID uuid, FileType fileType) {
		return uuid.toString() + getImageFileExt(fileType);
	}

	public String getFileName() {
		return file.getAbsolutePath();
	}
	
	// persistent data
	protected UUID uuid = null;
	protected boolean constrainAspect = true;
	protected Rect cropRect = new Rect();

	public UUID getUuid() {
		if (uuid == null)
			uuid = UUID.randomUUID();
		return uuid;
	}

	public Uri getFileUri() {
		if (file == null)
			return null;   // no picture selected yet
		else
			return Uri.fromFile(file);
	}
	
	public File getFile() {
		return file;
	}
	
	protected Bitmap getBitmap() {
		return bitmap;
	}
	
	public Matrix getMatrix() {
		return matrix;
	}

	public boolean getConstrainAspect() {
		return constrainAspect;
	}
	
	public FileType getFileType() {
		return getImageFileType(file.getName());
	}

	/**
	 * Construct a new image
	 * 
	 * @param transform
	 *            The current transformation
	 * @param x
	 *            Screen x coordinate
	 * @param y
	 *            Screen y coordinate
	 * @param penThickness
	 * @param penColor
	 */
	protected GraphicsImage(Transformation transform, float x, float y) {
		super(Tool.IMAGE);
		setTransform(transform);
		bottom_left = new Controlpoint(transform, x, y);
		bottom_right = new Controlpoint(transform, x, y);
		top_left = new Controlpoint(transform, x, y);
		top_right = new Controlpoint(transform, x, y);
		center = new Controlpoint(transform, x, y);
		controlpoints.add(bottom_left);
		controlpoints.add(bottom_right);
		controlpoints.add(top_left);
		controlpoints.add(top_right);
		controlpoints.add(center);
		init();
	}
	
	/**
	 * The copy constructor
	 * @param image
	 * @param dir the directory to copy the image file to
	 */
	protected GraphicsImage(final GraphicsImage image, File dir) {
		this(image);
		if (image.getFile() == null) 
			return;
		uuid = null;
		bitmap = null; //don't recycle the bitmaps between copy/pasted images. Should we?
		if (dir == null) 
			file = null;
		else { // make a copy of the image in the book directory dir
			final String fileName = getImageFileName(getUuid(), image.getFileType());
			file = new File(dir, fileName);
			com.write.Quill.image.Util.copyfile(image.getFile(), file);
		}
	}

	// without copying the file (e.g. for undo)
	protected GraphicsImage(final GraphicsImage image) {
		super(image);
		bottom_left = new Controlpoint(image.bottom_left);
		bottom_right = new Controlpoint(image.bottom_right);
		top_left = new Controlpoint(image.top_left);
		top_right = new Controlpoint(image.top_right);
		center = new Controlpoint(image.center);
		controlpoints.add(bottom_left);
		controlpoints.add(bottom_right);
		controlpoints.add(top_left);
		controlpoints.add(top_right);
		controlpoints.add(center);
		constrainAspect = image.constrainAspect;
		uuid = image.getUuid();
		file = image.getFile();
		bitmap = image.getBitmap();
		matrix = new Matrix(image.matrix);
		init();
	}

		public GraphicsControlpoint copyForUndo() {
		return new GraphicsImage(this);
	}
	
	private void init() {
		paint.setARGB(0xff, 0x5f, 0xff, 0x5f);
		paint.setStyle(Style.FILL);
		paint.setStrokeWidth(0);
		paint.setAntiAlias(true);
		paint.setStrokeCap(Paint.Cap.ROUND);
		outline.setARGB(0xff, 0x0, 0xaa, 0x0);
		outline.setStyle(Style.STROKE);
		outline.setStrokeWidth(4);
		outline.setAntiAlias(true);
		outline.setStrokeCap(Paint.Cap.ROUND);
	}
	
	public void halofy() {
		halo = true;
	}

	@Override
	protected Controlpoint initialControlpoint() {
		return bottom_right;
	}

	private RectF inverseRect() {
		return inverseRect(top_left, bottom_right);
	}
	
	private RectF inverseRect(Controlpoint a, Controlpoint b) {
		float points[] = {a.x, a.y, b.x, b.y};
		Matrix im = new Matrix();
		if(!matrix.invert(im))
			Log.v(TAG, "Non-invertible matrix in inverseRect");
		im.mapPoints(points);
		RectF r = new RectF(points[0], points[1], points[2], points[3]);
		//r.sort();
		return r;
	}
	
	private void normalizeMatrix(){
		RectF r = inverseRect();
		Matrix m = new Matrix();
		m.setRectToRect(new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), r, ScaleToFit.FILL);
		matrix.preConcat(m);
	}
	
	@Override
	public boolean intersects(RectF screenRect) {
		if (screenRect.contains(center.screenX(),center.screenY())) return true;
		float p[] = {transform.inverseX(screenRect.left), transform.inverseY(screenRect.top)};
		Matrix im = new Matrix();
		if(!matrix.invert(im))
			Log.v(TAG, "Non-invertible matrix in intersects");
		im.mapPoints(p);
		if (inverseRect().contains(p[0],p[1])) return true; 
		//none completely contains the other, check for edge intersections.
		if (GraphicsLine.lineIntersectsRectF(top_left.screenX(), top_left.screenY(), top_right.screenX(), top_right.screenY(), screenRect)) return true;
		if (GraphicsLine.lineIntersectsRectF(top_right.screenX(), top_right.screenY(), bottom_right.screenX(), bottom_right.screenY(), screenRect)) return true;
		if (GraphicsLine.lineIntersectsRectF(bottom_right.screenX(), bottom_right.screenY(), bottom_left.screenX(), bottom_left.screenY(), screenRect)) return true;
		if (GraphicsLine.lineIntersectsRectF(bottom_left.screenX(), bottom_left.screenY(), top_left.screenX(), top_left.screenY(), screenRect)) return true;
		return false;
	}
	
	public boolean intersects(Lasso lasso) {
		if (lasso.contains(center.screenX(),center.screenY())) return true;
		float p[] = {transform.inverseX(lasso.startX()), transform.inverseY(lasso.startY())};
		Matrix im = new Matrix();
		if(!matrix.invert(im))
			Log.v(TAG, "Non-invertible matrix in intersects");
		im.mapPoints(p);
		if (inverseRect().contains(p[0],p[1])) return true; 
		//none completely contains the other, check for edge intersections.
		if (lasso.intersectsSegment(top_left.screenX(), top_left.screenY(), top_right.screenX(), top_right.screenY())) return true;
		if (lasso.intersectsSegment(top_right.screenX(), top_right.screenY(), bottom_right.screenX(), bottom_right.screenY())) return true;
		if (lasso.intersectsSegment(bottom_right.screenX(), bottom_right.screenY(), bottom_left.screenX(), bottom_left.screenY())) return true;
		if (lasso.intersectsSegment(bottom_left.screenX(), bottom_left.screenY(), top_left.screenX(), top_left.screenY())) return true;
		return false;
	}
	
	public void applyMatrix(Matrix m) { // In screen coordinates
		applyNativeMatrix(transform.transformMatrix(m));
	}

	private void applyNativeMatrix(Matrix m) { // In native coordinates
		float points[] = new float[2*controlpoints.size()];
		int i = 0;
		for (Controlpoint p : controlpoints) {
			points[i] = p.x; i++;
			points[i] = p.y; i++;			
		}
		m.mapPoints(points);
		i=0;
		for (Controlpoint p : controlpoints) {
			p.x = points[i]; i++;
			p.y = points[i]; i++;			
		}
		matrix.postConcat(m);
	}

	@Override
	public void draw(Canvas c, RectF bounding_box) {
		if (file != null && bitmap == null)
			try {
				loadBitmap();
			} catch (IOException e) {
				Log.e(TAG, "loading bitmap: "+e.getMessage());
			}
		
		//computeScreenRect();
		c.clipRect(0, 0, c.getWidth(), c.getHeight(), android.graphics.Region.Op.REPLACE);

		if (halo) {
			Paint halopaint = new Paint();
			halopaint.setStyle(Style.STROKE);
			halopaint.setStrokeWidth(15);
			halopaint.setAntiAlias(true);
			halopaint.setStrokeCap(Paint.Cap.ROUND);
			halopaint.setARGB(0x70, 0x00, 0xff, 0x00);
			Path p = new Path();
			p.moveTo(top_left.screenX(), top_left.screenY());
			p.lineTo(top_right.screenX(), top_right.screenY());
			p.lineTo(bottom_right.screenX(), bottom_right.screenY());
			p.lineTo(bottom_left.screenX(), bottom_left.screenY());
			p.lineTo(top_left.screenX(), top_left.screenY());
			c.drawPath(p, halopaint);
		}
		if (bitmap == null) {
			Path p = new Path();
			p.moveTo(top_left.screenX(), top_left.screenY());
			p.lineTo(top_right.screenX(), top_right.screenY());
			p.lineTo(bottom_right.screenX(), bottom_right.screenY());
			p.lineTo(bottom_left.screenX(), bottom_left.screenY());
			p.lineTo(top_left.screenX(), top_left.screenY());
			c.drawPath(p, paint);
			c.drawPath(p, outline);
		} else {
			normalizeMatrix();
			Matrix screenM = new Matrix(matrix);
			screenM.postConcat(transform.getMatrix());
			c.drawBitmap(bitmap, screenM, null);
		}
	}

	private Controlpoint oppositeControlpoint(Controlpoint point) {
		if (point == bottom_right)
			return top_left;
		if (point == bottom_left)
			return top_right;
		if (point == top_right)
			return bottom_left;
		if (point == top_left)
			return bottom_right;
		if (point == center)
			return center;
		Assert.fail("Unreachable");
		return null;
	}
	
	private Controlpoint[] ccwFromControlpoint(Controlpoint point) {
		Controlpoint[] pts = {top_left, bottom_left, bottom_right, top_right}; 
		for (int i=0; i<4; i++) 
			if (pts[i] == point) {
				Controlpoint[] dst = new Controlpoint[4];
				System.arraycopy(pts, i, dst, 0, 4-i);
				System.arraycopy(pts, 0, dst, 4-i, i);
				return dst;
			}
		return pts;
	}
	
	private static float[] controlPointsToFloats(Controlpoint[] pts) {
		float fts[] = new float[2*pts.length];
		for (int i=0; i < pts.length; i++) {
			fts[2*i] = pts[i].x;
			fts[2*i+1] = pts[i].y;
		}
		return fts;
	}

	private static Controlpoint[] floatsToControlPoints(float[] fts) {
		Controlpoint pts[] = new Controlpoint[(fts.length/2)];
		for (int i=0; i < pts.length; i++) {
			pts[i].x = fts[2*i];
			pts[i].y = fts[2*i+1];
		}
		return pts;
	}

	private final static float minDistancePixel = 30;

	@Override
	void controlpointMoved(Controlpoint point) {
		super.controlpointMoved(point);
		if (point == center) {
			float oldX = (top_left.x + bottom_right.x) / 2;
			float oldY = (top_left.y + bottom_right.y) / 2;
			Matrix m = new Matrix();
			m.setTranslate(point.x-oldX, point.y-oldY);
			applyNativeMatrix(m);
			/*float width2 = (bottom_right.x - bottom_left.x) / 2;
			float height2 = (top_right.y - bottom_right.y) / 2;
			bottom_right.y = bottom_left.y = center.y - height2;
			top_right.y = top_left.y = center.y + height2;
			bottom_right.x = top_right.x = center.x + width2;
			bottom_left.x = top_left.x = center.x - width2;*/
		} else {
			Controlpoint opposite = oppositeControlpoint(point);
			Controlpoint[] pts = ccwFromControlpoint(point);
			//Log.d(TAG, "point "+point.toString()+" opp "+opposite.toString());
			RectF rr = inverseRect(pts[2], pts[0]);
			RectF rrc = inverseRect(pts[2], center);
			//Log.d(TAG, "rr "+rr.toString());
			//Log.d(TAG, "rrc "+rrc.toString());
			float oldScaling;
			if (bitmap != null)
				oldScaling = FloatMath.sqrt(((center.x-opposite.x)*(center.x-opposite.x) +
                    (center.y-opposite.y)*(center.y-opposite.y)) /
                    (rrc.height()*rrc.height() + rrc.width()*rrc.width()));
			else
				oldScaling = 1;
			float dx = (rr.right-rr.left)*oldScaling;
			float dy = (rr.bottom-rr.top)*oldScaling;
			float minDistance = minDistancePixel / scale;
			if (-minDistance <= dx && dx <= minDistance) {
				float sgn = Math.signum(dx);
				opposite.x = point.x + sgn * minDistance;
				dx = sgn * minDistance;
			}
			if (-minDistance <= dy && dy <= minDistance) {
				float sgn = Math.signum(dy);
				opposite.y = point.y + sgn * minDistance;
				dy = sgn *minDistance;
			}
			if (constrainAspect && bitmap != null) {
				float r = (Math.abs(dx)+Math.abs(dy))/2;
				dx = r * sqrtAspect * Math.signum(dx);
				dy = r / sqrtAspect * Math.signum(dy);
				// Log.d(TAG, "move "+dx + " "+dy + " " + r + " "+(sqrtAspect*sqrtAspect));
			}
			rrc = new RectF(rr.left, rr.top, rr.left+dx/oldScaling, rr.top+dy/oldScaling);
			rrc.sort();
			float points[] = {rrc.left, rrc.top,
							  rrc.right, rrc.top,
							  rrc.right,rrc.bottom,
							  rrc.left,rrc.bottom,
							  };
			//Log.d(TAG, "oscaling "+oldScaling);
			//Log.d(TAG, "points "+points[0]+" "+points[1]+" "+points[4]+" "+points[5]);
			//Log.d(TAG, "matrix "+matrix.toString());
			matrix.mapPoints(points);
			top_left.x = points[0]; top_left.y = points[1];
			top_right.x = points[2]; top_right.y = points[3];
			bottom_right.x = points[4]; bottom_right.y = points[5];
			bottom_left.x = points[6]; bottom_left.y = points[7];
			center.x = (top_left.x + bottom_right.x)/2;
			center.y = (top_left.y + bottom_right.y)/2;
			//Log.d(TAG, "move "+dx + " "+dy);
			//Log.d(TAG, "after "+points[0]+" "+points[1]+" "+points[4]+" "+points[5]);
			//Log.d(TAG, "after "+top_left.toString()+" "+top_right.toString()+" "+
			//			bottom_right.toString()+" "+bottom_left.toString());			
			
			/*rectF.bottom = opposite.y;
			rectF.top = opposite.y - dy;
			rectF.left = opposite.x;
			rectF.right = opposite.x - dx;
			rectF.sort();
			bottom_right.y = bottom_left.y = rectF.bottom;
			top_right.y = top_left.y = rectF.top;
			bottom_right.x = top_right.x = rectF.right;
			bottom_left.x = top_left.x = rectF.left;
			center.x = rectF.left + (rectF.right - rectF.left) / 2;
			center.y = rectF.bottom + (rectF.top - rectF.bottom) / 2;*/
		}
	}

	private void computeScreenRect() {
		rectF.bottom = bottom_left.screenY();
		rectF.top = top_left.screenY();
		rectF.left = bottom_left.screenX();
		rectF.right = bottom_right.screenX();
		rectF.sort();
		rectF.round(rect);
	}

	public void writeToStream(DataOutputStream out) throws IOException {
		int protocol;
		if (top_left.x == bottom_left.x && top_left.y == top_right.y)
			protocol = 1;  // protocol #1
		else
			protocol = 2;  // protocol #2
		out.writeInt(protocol);
		out.writeUTF(uuid.toString());
		out.writeFloat(top_left.x);
		out.writeFloat(bottom_right.x);
		out.writeFloat(top_left.y);
		out.writeFloat(bottom_right.y);
		out.writeBoolean(constrainAspect);
		if (protocol == 2) {
			float[] mValues = new float[9];
			matrix.getValues(mValues);
			for (int i = 0; i<9; i++)
				out.writeFloat(mValues[i]);
		}
	}
	
	public String getDir() {
		if (file == null)
			return "";
		else
			return file.getParent();
	}

	public GraphicsImage(DataInputStream in, File dir) throws IOException {
		super(Tool.IMAGE);
		int version = in.readInt();
		if (version > 2)
			throw new IOException("Unknown image version!");

		uuid = UUID.fromString(in.readUTF());
		float left   = in.readFloat(); 
		float right  = in.readFloat();
		float top    = in.readFloat();
		float bottom = in.readFloat();  		
		constrainAspect = in.readBoolean();
		if (version == 2) {
			float[] mValues = new float[9];
			for (int i = 0; i<9; i++)
				mValues[i] = in.readFloat();
			matrix.setValues(mValues);
		}
		
		bottom_right = new Controlpoint(transform, right, bottom);
		top_left = new Controlpoint(transform, left, top);
		bottom_left = new Controlpoint(transform, left, bottom);
		top_right = new Controlpoint(transform, right, top);
		center = new Controlpoint(transform, (left+right)/2, (top+bottom)/2);
		controlpoints.add(bottom_left);
		controlpoints.add(bottom_right);
		controlpoints.add(top_left);
		controlpoints.add(top_right);
		controlpoints.add(center);
		init();
		if (dir == null)
			file = null;
		else
			file = new File(dir, getImageFileName(uuid, FileType.FILETYPE_JPG));
		if (version == 2)
			controlpointMoved(bottom_right);
	}

	@Override
	public void render(Artist artist) {
		if (matrix.isIdentity())
			artist.imageJpeg(file, top_left.x, top_right.x, top_left.y, bottom_left.y);
		else
			artist.imageJpeg(file, matrix, bitmap.getWidth(), bitmap.getHeight());
	}

	public boolean checkFileName(String fileName) {
		FileType fileType = getImageFileType(fileName);
		return fileName.endsWith(getImageFileName(uuid, fileType));
	}
	
	public void setFile(String fileName, boolean constrainAspect) {
		// file = new File("/mnt/sdcard/d5efe912-4b03-4ed7-a124-bff4984691d6.jpg");
		if (!checkFileName(fileName)) {
			Log.e(TAG, "filename must be uuid.ext");
		}
		file = new File(fileName);
		try {
			loadBitmap();
		} catch (IOException e) {
			Log.e(TAG, "Unable to load file " + file.toString() + " (missing?");
		}
		this.constrainAspect = constrainAspect;
		if (constrainAspect) {
			float w = top_right.x - top_left.x;
			float h = bottom_right.y - top_right.y;
			float r = (w+h)/2f;
			w = r * sqrtAspect;
			h = r / sqrtAspect;
			bottom_left.x = top_left.x = center.x - w/2;
			bottom_right.x = top_right.x = center.x + w/2;
			bottom_left.y = bottom_right.y = center.y + h/2;
			top_left.y = top_right.y = center.y - h/2;
		}
	}

	private final int IMAGE_MAX_SIZE = 1024;

	private void loadBitmap() throws IOException {
		Assert.assertNotNull(file);
		InputStream fis;

		BitmapFactory.Options o1 = new BitmapFactory.Options();
		o1.inJustDecodeBounds = true;
		fis = new FileInputStream(file);
		BitmapFactory.decodeStream(fis, null, o1);
		fis.close();

		int h = o1.outHeight;
		int w = o1.outWidth;
		int scale = 1;
		if (h > IMAGE_MAX_SIZE || w > IMAGE_MAX_SIZE) {
			scale = (int) Math.pow(
					2,
					(int) Math.round(Math.log(IMAGE_MAX_SIZE
							/ (double) Math.max(h, w))
							/ Math.log(0.5)));
		}

		BitmapFactory.Options o2 = new BitmapFactory.Options();
		o2.inSampleSize = scale;
		fis = new FileInputStream(file);
		try {
			bitmap = BitmapFactory.decodeStream(fis, null, o2);
		} catch (OutOfMemoryError e1) {
			o2.inSampleSize = 8*scale;
			try {
				bitmap = BitmapFactory.decodeStream(fis, null, o2);
			} catch (OutOfMemoryError e2) {
				Log.e(TAG, "Not enough memory to load image");
				bitmap = null;
			}
		}
		fis.close();

		height = o2.outHeight;
		width = o2.outWidth;
		sqrtAspect = FloatMath.sqrt((float)width / (float)height);
	}

		
}
