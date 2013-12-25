package name.vbraun.view.write;

import java.util.LinkedList;

public interface GraphicsModifiedListener {
	public void onGraphicsCreateListener(Page page, Graphics toAdd);
	public void onGraphicsModifyListener(Page page, 
			LinkedList<? extends Graphics> toRemove, 
			LinkedList<? extends Graphics> toReplaceWith);
	public void onGraphicsEraseListener(Page page, Graphics toErase);
	
	public void onPageClearListener(Page page);
}
