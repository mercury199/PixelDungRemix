package com.watabou.noosa;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextPaint;

import com.watabou.glwrap.Matrix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SystemText extends Text {

	protected String text;

	protected TextPaint textPaint = new TextPaint();

	private ArrayList<SystemTextLine> lineImage = new ArrayList<>();

	private static Set<SystemText> texts = new HashSet<>();

	private final static float oversample = 2f;
	private boolean needWidth = false;
	private float fontScale;

	public SystemText(float baseLine) {
		this("", baseLine, false);
	}

	public SystemText(String text, float baseLine, boolean multiline) {
		this(text, baseLine, multiline, 1 / oversample);
	}

	public SystemText(String text, float baseLine, boolean multiline,
	                  float scale) {
		super(0, 0, 0, 0);

		fontScale = scale;

		super.setScale(scale, scale);

		needWidth = multiline;

		float size = baseLine;

		if (size == 0) {
			throw new RuntimeException("zero sized font!!!");
		}

		Typeface tf = Typeface.create((String) null, Typeface.BOLD);

		textPaint.setTextSize(size * oversample);
		textPaint.setAntiAlias(true);

		textPaint.setTypeface(tf);

		textPaint.setColor(0xffffffff);

		this.text(text);
		texts.add(this);
	}

	private void destroyLines() {
		for (SystemTextLine img : lineImage) {
			if (getParent() != null) {
				getParent().remove(img);
			}
			img.destroy();
		}
	}

	@Override
	public void destroy() {
		destroyLines();

		text = null;
		super.destroy();
		texts.remove(this);
	}

	private ArrayList<Float> xCharPos = new ArrayList<>();

	private float fontHeight;

	private int fillLine(int startFrom) {
		int offset = startFrom;

		float xPos = 0;
		xCharPos.clear();

		final int length = text.length();
		int lastWordOffset = offset;

		for (; offset < length; ) {
			final int codepoint = text.codePointAt(offset);
			int codepointCharCount = Character.charCount(codepoint);

			xCharPos.add(xPos);

			float xDelta = symbolWidth(text.substring(offset, offset
					+ codepointCharCount));

			offset += codepointCharCount;

			if (Character.isWhitespace(codepoint)) {
				lastWordOffset = offset;
			}

			if (codepoint == 0x000A) {
				return offset;
			}

			xPos += xDelta;

			if (maxWidth != Integer.MAX_VALUE
					&& xPos > maxWidth / scale.x) {
				if (lastWordOffset != startFrom) {
					return lastWordOffset;
				} else {
					return offset - 1;
				}
			}
		}
		xCharPos.add(xPos);
		// Log.d("SystemText", String.format("eot"));
		return offset;
	}

	@SuppressLint("NewApi")
	private void createText() {
		if (text == null) {
			return;
		}

		if (needWidth == true && maxWidth == Integer.MAX_VALUE) {
			return;
		}

		if (fontHeight > 0) {
			// Log.d("SystemText", String.format("xscale: %3.2f %s", scale.x,
			// text));

			destroyLines();

			lineImage.clear();

			width = height = 0;

			int charIndex = 0;
			int startLine = 0;

			while (startLine < text.length()) {
				int nextLine = fillLine(startLine);
				/*
				 * Log.d("SystemText", String.format(Locale.ROOT, "width: %d",
				 * maxWidth));
				 * 
				 * Log.d("SystemText", String.format(Locale.ROOT,
				 * "processed: %d - %d -> %s", startLine, nextLine,
				 * text.substring(startLine, nextLine)));
				 */
				float lineWidth = 0;

				if (nextLine > 0) {
					lineWidth = xCharPos.get(xCharPos.size() - 1);
					width = Math.max(lineWidth, width);

					// Log.d("SystemText",
					// String.format(Locale.ROOT, "lineWidth : %3f %3f",
					// lineWidth, width));
				}

				height += fontHeight;

				if (lineWidth > 0) {

					Bitmap bitmap = Bitmap.createBitmap(
							(int) (lineWidth * oversample),
							(int) (fontHeight * oversample),
							Bitmap.Config.ARGB_4444);

					Canvas canvas = new Canvas(bitmap);

					int offset = startLine;
					int lineCounter = 0;
					for (; offset < nextLine; ) {
						final int codepoint = text.codePointAt(offset);
						int codepointCharCount = Character.charCount(codepoint);

						if (Character.isWhitespace(codepoint)) {

						} else {
							if (mask == null
									|| (charIndex < mask.length && mask[charIndex])) {
								// Log.d("SystemText",
								// String.format("symbol: %s %d %d %3.1f",
								// text.substring(offset, offset
								// + codepointCharCount), offset, charIndex,
								// xCharPos.get(lineCounter) ));

								canvas.drawText(
										text.substring(offset, offset + codepointCharCount),
										xCharPos.get(lineCounter) * oversample,
										// textPaint.descent() * oversample,
										(fontHeight) * oversample - textPaint.descent(),
										textPaint);
							}
							charIndex++;
						}

						lineCounter++;
						offset += codepointCharCount;
					}
					SystemTextLine line = new SystemTextLine(bitmap);
					line.setVisible(getVisible());
					lineImage.add(line);
				} else {
					lineImage.add(new SystemTextLine());
				}
				startLine = nextLine;
			}
			
/*			Log.d("SystemText", String.format(Locale.ROOT,
					"%3.1f x %3.1f (max: %3.1f, lines: %d) -> %s", width,
					height, maxWidth / scale.x, lineImage.size(), text));
*/
		}
	}

	@Override
	protected void updateMatrix() {
		// "origin" field is ignored
		Matrix.setIdentity(matrix);
		Matrix.translate(matrix, x, y);
		Matrix.scale(matrix, scale.x, scale.y);
		Matrix.rotate(matrix, angle);
	}

	private void updateParent() {
		Group parent = getParent();
		for (SystemTextLine img : lineImage) {
			if (img.getParent() != parent) {
				if (img.getParent() != null) {
					img.getParent().remove(img);
				}

				if (parent != null) {
					parent.add(img);
				}
			}
		}
	}

	@Override
	public void setParent(Group parent) {
		super.setParent(parent);

		updateParent();
	}

	@Override
	public boolean setVisible(boolean visible) {
		if (lineImage != null) {
			for (SystemTextLine img : lineImage) {
				img.setVisible(visible);
			}
		}
		return super.setVisible(visible);
	}

	@Override
	public void draw() {
		measure();
		if (lineImage != null) {
			int line = 0;

			updateParent();

			for (SystemTextLine img : lineImage) {

				// Log.d("SystemText", String.format(Locale.ROOT,
				// "%3.1f x %3.1f -> %s", x, y, text));

				img.ra = ra;
				img.ga = ga;
				img.ba = ba;
				img.rm = rm;
				img.gm = gm;
				img.bm = bm;
				img.am = am;
				img.aa = aa;

				img.setPos(x, y + (line * fontHeight) * scale.y);
				img.setScale(scale.x / oversample, scale.x / oversample);

				line++;
			}
		}
	}

	protected float symbolWidth(String symbol) {
		return textPaint.measureText(symbol) / oversample;
	}

	public void measure() {
		if (Math.abs(scale.x) < 0.001) {
			return;
		}

		if (dirty) {
			dirty = false;
			if (text == null) {
				text = "";
			}

			fontHeight = (textPaint.descent() - textPaint.ascent())
					/ oversample;
			createText();
		}
	}

	@Override
	public void setScale(float x, float y) {
		super.setScale(fontScale * x, fontScale * y);
	}

	public String text() {
		return text;
	}

	public void text(String str) {
		dirty = true;
		text = str;
		measure();
	}

	@Override
	public float baseLine() {
		return height * scale.y;
	}

	public static void invalidate() {
		for (SystemText txt : texts) {
			txt.dirty = true;
			txt.destroyLines();
		}
	}
}
