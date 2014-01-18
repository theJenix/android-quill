package name.vbraun.view.write;

import android.graphics.RectF;

public class Lasso {
	float[] pos_x;
	float[] pos_y;
	int Nmax = 1024;
	int N=0;
	RectF boundingBox = new RectF();
	
	public Lasso(float x, float y) {
		pos_x = new float[Nmax+1];
		pos_y = new float[Nmax+1];
		pos_x[0] = x;
		pos_y[0] = y;
		N = 1;
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
		pos_x[N] = pos_x[0];
		pos_y[N] = pos_y[0];
		int cnt = 0;
		int a,b; //a above b
		for (int i=0; i<N; i++) {
			if (pos_y[i] >= pos_y[i+1]) {
				a = i;
				b = i+1;
			} else {
				a = i+1;
				b = i;
			}
			if ((y < pos_y[a]) && (y > pos_y[b])) {
				if (x*(pos_y[a]-pos_y[b]) < (pos_y[a]-y)*pos_x[a] + (y-pos_y[b])*pos_x[b])
					cnt += 1;
			} else if (y == pos_y[b] && y < pos_y[a] && x < pos_x[b])
				cnt += 1;
		}
		return (cnt % 2 == 1);
	}

	public boolean intersectsSegment(float x1, float y1, float x2, float y2) {
		if (!GraphicsLine.lineIntersectsRectF(x1, y1, x2, y2, boundingBox))
			return false;
		for(int i=1; i<N; i++) {
			RectF r = new RectF(pos_x[i-1],pos_y[i-1],pos_x[i],pos_y[i]);
			r.sort();
			if (GraphicsLine.lineIntersectsRectF(x1, y1, x2, y2, r))
				return true;
		}
		if (contains(x1,y1) || contains(x2,y2))
			return true;
		return false;
	}
}
