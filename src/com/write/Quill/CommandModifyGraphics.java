package com.write.Quill;

import java.util.LinkedList;

import com.write.Quill.data.Bookshelf;

import name.vbraun.view.write.Graphics;
import name.vbraun.view.write.Page;
import name.vbraun.view.write.Stroke;

public class CommandModifyGraphics extends Command {

	protected final LinkedList <? extends Graphics> graphicsOld, graphicsNew;
	
	public CommandModifyGraphics(Page page, 
			LinkedList<? extends Graphics> toErase, 
			LinkedList<? extends Graphics> toReCreate) {
		super(page);
		graphicsOld = toErase;
		graphicsNew = toReCreate;
	}

	public CommandModifyGraphics(Page page, Graphics toErase, Graphics toReCreate) {
		super(page);
		LinkedList<Graphics> gOld = new LinkedList<Graphics> ();
		gOld.add(toErase);
		graphicsOld = gOld;
		LinkedList<Graphics> gNew = new LinkedList<Graphics> ();
		gNew.add(toReCreate);
		graphicsNew = gNew;
	}

	@Override
	public void execute() {
		for (Graphics g: graphicsOld) 
			UndoManager.getApplication().remove(getPage(), g);
		for (Graphics g: graphicsNew) 
			UndoManager.getApplication().add(getPage(), g);		
	}

	@Override
	public void revert() {
		for (Graphics g: graphicsNew) 
			UndoManager.getApplication().remove(getPage(), g);
		for (Graphics g: graphicsOld) 
			UndoManager.getApplication().add(getPage(), g);		
	}

	@Override
	public String toString() {
		int n = Bookshelf.getCurrentBook().getPageNumber(getPage());
		QuillWriterActivity app = UndoManager.getApplication();
		return app.getString(R.string.command_modify_graphics, n);
	}
	
}
