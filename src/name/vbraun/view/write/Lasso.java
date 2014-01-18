package name.vbraun.view.write;

import android.graphics.RectF;
import android.util.Log;

public class Lasso {
	float[] pos_x;
	float[] pos_y;
	int Nmax = 1024;
	int N=0;
	RectF boundingBox = new RectF();
	boolean below = false; //lasso contains everything below open curve
	
	public Lasso(float x, float y) {
		pos_x = new float[Nmax+1];
		pos_y = new float[Nmax+1];
		pos_x[0] = x;
		pos_y[0] = y;
		N = 1;
	}

	public void setBelow() {
		below = true;
		boundingBox.union(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
		boundingBox.union(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
	}

	public boolean getBelow() {
		return below;
	}
	
	public float startX() {
		return pos_x[0];
	}
	
	public float startY() {
		return pos_y[0];
	}
	
	public RectF getBoundingBox () {
		return boundingBox;
	}

	public boolean full() {
		return N >= Nmax;
	}
	
	public void add(float x, float y) {
		if (N >= Nmax) return;
		pos_x[N] = x;
		pos_y[N] = y;
		N += 1;
		if (N == 2) {
			boundingBox.set(pos_x[0], pos_y[0], pos_x[1], pos_y[1]);
			boundingBox.sort();
		}
		boundingBox.union(x, y);
	}
	
	public boolean contains(float x, float y) {
		if (!boundingBox.contains(x, y)) return false;
		if(below) {
			pos_x[N] = (pos_x[0]<pos_x[N-1])?Float.POSITIVE_INFINITY:Float.NEGATIVE_INFINITY;
			pos_y[N] = pos_y[N-1];			
		} else {
			pos_x[N] = pos_x[0];
			pos_y[N] = pos_y[0];
		}
		int cnt = 0;
		if (below)
			if(((x < pos_x[0]) && (pos_x[0]<pos_x[N-1])) || ((x >= pos_x[0]) && (pos_x[0]>=pos_x[N-1])))
				if (y > pos_y[0])
					cnt = 1;
		int a,b; //a right b
		for (int i=0; i<N; i++) {
			if (pos_x[i] >= pos_x[i+1]) {
				a = i;
				b = i+1;
			} else {
				a = i+1;
				b = i;
			}
			if ((x < pos_x[a]) && (x > pos_x[b])) {
				if (y*(pos_x[a]-pos_x[b]) > (pos_x[a]-x)*pos_y[b] + (x-pos_x[b])*pos_y[a]) {
					cnt += 1;
				}
			} else if (x == pos_x[b] && x < pos_x[a] && y > pos_y[b])
				cnt += 1;
		}
		return (cnt % 2 == 1);
	}

	public boolean containsSegment(float x1, float y1, float x2, float y2) {
		if (!boundingBox.contains(x1, y1) || !boundingBox.contains(x2, y2))
			return false;
		if(below) {
			pos_x[N] = (pos_x[0]<pos_x[N-1])?Float.POSITIVE_INFINITY:Float.NEGATIVE_INFINITY;
			pos_y[N] = pos_y[N-1];			
		} else {
			pos_x[N] = pos_x[0];
			pos_y[N] = pos_y[0];
		}
		for(int i=0; i<N; i++) {
			if (GraphicsLine.lineIntersectsLine(x1, y1, x2, y2, 
					pos_x[i+1],pos_y[i+1],pos_x[i],pos_y[i]))
				return false;
		}
		if (!contains(x1,y1) || !contains(x2,y2))
			return false;
		return true;
	}

public boolean intersectsSegment(float x1, float y1, float x2, float y2) {
		if (!GraphicsLine.lineIntersectsRectF(x1, y1, x2, y2, boundingBox))
			return false;
		if(below) {
			pos_x[N] = (pos_x[0]<pos_x[N-1])?Float.POSITIVE_INFINITY:Float.NEGATIVE_INFINITY;
			pos_y[N] = pos_y[N-1];			
		} else {
			pos_x[N] = pos_x[0];
			pos_y[N] = pos_y[0];
		}
		for(int i=0; i<N; i++) {
			if (GraphicsLine.lineIntersectsLine(x1, y1, x2, y2, 
					pos_x[i+1],pos_y[i+1],pos_x[i],pos_y[i]))
				return true;
		}
		if (contains(x1,y1) || contains(x2,y2))
			return true;
		return false;
	}
}
