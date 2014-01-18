package name.vbraun.view.write;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import com.write.Quill.artist.Artist;
import com.write.Quill.artist.LineStyle;

import junit.framework.Assert;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

public class GraphicsLine extends GraphicsControlpoint {
	private static final String TAG = "GraphicsLine";
	
	private Controlpoint p0, p1;
	private final Paint pen = new Paint();
	private int pen_thickness;
	private int pen_color;
	
	/**
	 * Construct a new line
	 * 
	 * @param transform The current transformation
	 * @param x Screen x coordinate 
	 * @param y Screen y coordinate 
	 * @param penThickness
	 * @param penColor
	 */
	protected GraphicsLine(Transformation transform, float x, float y, int penThickness, int penColor) {
		super(Tool.LINE);
		setTransform(transform);
		p0 = new Controlpoint(transform, x, y);
		p1 = new Controlpoint(transform, x, y);
		controlpoints.add(p0);
		controlpoints.add(p1);
		setPen(penThickness, penColor);
	}
	
	/**
	 * Copy constructor
	 * @param line
	 */
	protected GraphicsLine(final GraphicsLine line) {
		super(line);
		p0 = new Controlpoint(line.p0);
		p1 = new Controlpoint(line.p1);
		controlpoints.add(p0);
		controlpoints.add(p1);
		setPen(line.pen_thickness, line.pen_color);
	}

	public GraphicsControlpoint copyForUndo() {
	return new GraphicsLine(this);
}

	@Override
	protected Controlpoint initialControlpoint() {
		return p1;
	}

	private void setPen(int new_pen_thickness, int new_pen_color) {
		pen_thickness = new_pen_thickness;
		pen_color = new_pen_color;
		pen.setARGB(Color.alpha(pen_color), Color.red(pen_color), 
					 Color.green(pen_color), Color.blue(pen_color));
		pen.setAntiAlias(true);
		pen.setStrokeCap(Paint.Cap.ROUND);
		recompute_bounding_box = true;
	}
	
	public void setPenColor(int new_pen_color) {
		pen_color = new_pen_color;
		pen.setARGB(Color.alpha(pen_color), Color.red(pen_color), Color.green(pen_color), Color.blue(pen_color));
	}
	
	public void setPenThickness(int new_pen_thickness) {
		pen_thickness = new_pen_thickness;
		recompute_bounding_box = true;
	}
	
	public void halofy() {
		// Thicken and color in green
		setPen(pen_thickness+15, 0x7000ff00);
	}

	// this computes the argument to Paint.setStrokeWidth()
	public float getScaledPenThickness() {
		return Stroke.getScaledPenThickness(scale, pen_thickness);
	}
	
	public float getScaledPenThickness(float scale) {
		return Stroke.getScaledPenThickness(scale, pen_thickness);
	}

	protected float boundingBoxInset() { 
		return -getScaledPenThickness()/2 - 1;
	}
	
	
	@Override
	public boolean intersects(RectF screenRect) {
		float x0 = p0.screenX();
		float x1 = p1.screenX();
		float y0 = p0.screenY();
		float y1 = p1.screenY();
		return lineIntersectsRectF(x0, y0, x1, y1, screenRect);
	}
	
	public boolean isIn(RectF r_screen) {
		return (r_screen.contains(getBoundingBox()));
	}
	
	public boolean intersects(Lasso lasso) {
		return lasso.intersectsSegment(p0.screenX(), p0.screenY(), p1.screenX(), p1.screenY());
	}
	
	public boolean isIn(Lasso lasso) {
		return lasso.containsSegment(p0.screenX(), p0.screenY(), p1.screenX(), p1.screenY());
	}
	
	public static boolean lineIntersectsRectF(float x0, float y0, float x1, float y1, RectF rect) { 
		// f(x,y) = (y1-y0)*x - (x1-x0)*y + x1*y0-x0*y1
		float dx = x1-x0;
		float dy = y1-y0;
		float constant = x1*y0-x0*y1;
		float f1 = dy * rect.left  - dx * rect.bottom + constant;
		float f2 = dy * rect.left  - dx * rect.top    + constant;
		float f3 = dy * rect.right - dx * rect.bottom + constant;
		float f4 = dy * rect.right - dx * rect.top    + constant;
		boolean allNegative = (f1<0) && (f2<0) && (f3<0) && (f4<0);
		boolean allPositive = (f1>0) && (f2>0) && (f3>0) && (f4>0);
		if (allNegative || allPositive) return false;
		// rect intersects the infinite line, check segment endpoints
		float xMin = Math.min(x0, x1);
		if (xMin > rect.right) return false;
		float xMax = Math.max(x0, x1);
		if (xMax < rect.left) return false;
		float yMin = Math.min(y0, y1);
		if (yMin > rect.bottom) return false;
		float yMax = Math.max(y0, y1);
		if (yMax < rect.top) return false;
		return true;
	}
	
	private static float nabla(float x1,float y1,float x2,float y2,float x3, float y3) {
		return (x1*(y2-y3)-x2*(y1-y3)+x3*(y1-y2));
	}

	public static boolean lineIntersectsLine(float ax1, float ay1,float ax2, float ay2,
			float bx1, float by1,float bx2, float by2) {
		return nabla(ax1,ay1,ax2,ay2,bx1,by1)*nabla(ax1,ay1,ax2,ay2,bx2,by2) <= 0 &&
				nabla(bx1,by1,bx2,by2,ax1,ay1)*nabla(bx1,by1,bx2,by2,ax2,ay2) <= 0;
	}

	public void translate(float dx, float dy) { // In screen coordinates
		p0.translate(dx, dy);
		p1.translate(dx, dy);
		recompute_bounding_box = true;
	}

	public void applyMatrix(Matrix m) { // In screen coordinates
		Matrix tm = transform.transformMatrix(m);
		float points[] = {p0.x,p0.y,p1.x,p1.y};
		tm.mapPoints(points);
		p0.x = points[0];
		p0.y = points[1];
		p1.x = points[2];
		p1.y = points[3];
		recompute_bounding_box = true;
	}

	@Override
	public void draw(Canvas c, RectF bounding_box) {
		final float scaled_pen_thickness = getScaledPenThickness();
		pen.setStrokeWidth(scaled_pen_thickness);
		float x0, x1, y0, y1;
		// note: we offset the first point by 1/10 pixel since android does not draw lines with start=end
		x0 = p0.screenX() + 0.1f;
		x1 = p1.screenX();
		y0 = p0.screenY();
		y1 = p1.screenY();
		// Log.v(TAG, "Line ("+x0+","+y0+") -> ("+x1+","+y1+"), thickness="+scaled_pen_thickness);

		// On some devices, hardware acceleration prevents the caps from being drawn properly
		// see https://code.google.com/p/android/issues/detail?id=24873
		c.drawLine(x0, y0, x1, y1, pen);
	}

	
	public void writeToStream(DataOutputStream out) throws IOException {
		out.writeInt(1);  // protocol #1
		out.writeInt(pen_color);
		out.writeInt(pen_thickness);
		out.writeInt(tool.ordinal());
		out.writeFloat(p0.x);
		out.writeFloat(p0.y);
		out.writeFloat(p1.x);
		out.writeFloat(p1.y);
	}
	
	public GraphicsLine(DataInputStream in) throws IOException {
		super(Tool.LINE);
		int version = in.readInt();
		if (version > 1)
			throw new IOException("Unknown line version!");
		pen_color = in.readInt();
		pen_thickness = in.readInt();
		tool = Tool.values()[in.readInt()];
		if (tool != Tool.LINE)
			throw new IOException("Unknown tool type!");
		
		p0 = new Controlpoint(in.readFloat(), in.readFloat());
		p1 = new Controlpoint(in.readFloat(), in.readFloat());
		controlpoints.add(p0);
		controlpoints.add(p1);
		setPen(pen_thickness, pen_color);
	}
	
	@Override
	public void render(Artist artist) {
		LineStyle line = new LineStyle();
		float scaled_pen_thickness = getScaledPenThickness(1f);
		line.setWidth(scaled_pen_thickness);
		line.setCap(LineStyle.Cap.ROUND_END);
		line.setJoin(LineStyle.Join.ROUND_JOIN);
        float red  = Color.red(pen_color)/(float)0xff;
        float green = Color.green(pen_color)/(float)0xff;
        float blue = Color.blue(pen_color)/(float)0xff;
		line.setColor(red, green, blue);
		artist.setLineStyle(line);
		artist.moveTo(p0.x, p0.y);
		artist.lineTo(p1.x, p1.y);
		artist.stroke();
	}

}
