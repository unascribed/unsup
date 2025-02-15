package com.unascribed.sup.opengl.pieces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.brotli.dec.BrotliInputStream;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;

import com.unascribed.sup.Puppet;
import com.unascribed.sup.Util;
import com.unascribed.sup.opengl.GLPuppet;

import static com.unascribed.sup.opengl.util.GL.*;
import static org.lwjgl.util.freetype.FreeType.*;
import static org.lwjgl.system.MemoryUtil.*;

public class FontManager {
	
	public double dpiScale = 1;
	private Map<String, FT_Face> ftFaces = new HashMap<>();
	private long ftLibrary;
	private FT_Bitmap scratchBitmap;
	
	private Map<CacheKey, CachedTexture> cachedTextures = new HashMap<>();
	
	public enum Face {
		REGULAR("FiraSans-Regular.ttf.br", "NotoSansCJK-Regular.ttc"),
		BOLD("FiraSans-Bold.ttf.br", "NotoSansCJK-Bold.ttc"),
		ITALIC("FiraSans-Italic.ttf.br", "NotoSansCJK-Regular.ttc"),
		BOLDITALIC("FiraSans-BoldItalic.ttf.br", "NotoSansCJK-Bold.ttc"),
		;
		public final String[] filenames;

		Face(String... filenames) {
			this.filenames = filenames;
		}
		
	}
	
	public FontManager() {
		{
			PointerBuffer libraryBuf = memAllocPointer(1);
			int ftError = FT_Init_FreeType(libraryBuf);
			if (ftError != 0) {
				throw new RuntimeException("Failed to initialize FreeType: "+FT_Error_String(ftError));
			}
			ftLibrary = libraryBuf.get(0);
			memFree(libraryBuf);
		}
		
		scratchBitmap = FT_Bitmap.malloc();
		PointerBuffer buf = memPointerBuffer(scratchBitmap.address()+FT_Bitmap.BUFFER, 1);
		buf.put(memAlloc(1));
	}
	
	public float[] drawWrapped(Face f, float baseX, float x, float y, float size, float maxWidth, String str) {
		String[] words = str.split(" ");
		float spaceWidth = measureString(f, size, " ");
		for (String word : words) {
			float w = measureString(f, size, word);
			if (x+w >= baseX+maxWidth) {
				x = baseX;
				y += alignToScreenPixel(size*1.2f);
			}
			drawString(f, x, y, size, word);
			x += w;
			x += spaceWidth;
		}
		if (!str.endsWith(" ")) {
			x -= spaceWidth;
		}
		return new float[] { x, y };
	}
	
	public float drawString(Face f, float x, float y, float size, String str) {
		if (dpiScale != 1) {
			glPushMatrix();
			x = alignToScreenPixel((x+getState().glTranslationX)*dpiScale);
			y = alignToScreenPixel((y+getState().glTranslationY)*dpiScale);
			size = (float)(size*dpiScale);
			glLoadIdentity();
			glTranslatef(0, 0, -200);
		}
		
		float totalW = 0;
		int ftSize = (int)(size*64*dpiScale);
		glEnable(GL_TEXTURE_2D);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glEnable(GL_BLEND);
		for (int i = 0; i < str.length(); i++) {
			char chr = str.charAt(i);
			CacheKey key = new CacheKey(f, ftSize, chr);
			CachedTexture ct = cachedTextures.get(key);
			if (ct == null) {
				ct = new CachedTexture();
				FT_Face ftFace = chooseFace(f, chr, ftSize);
				if (ftFace == null) continue;
				if (FT_Bitmap_Convert(ftLibrary, ftFace.glyph().bitmap(), scratchBitmap, 4) != 0) continue;
				ct.x = ftFace.glyph().bitmap_left();
				ct.y = ftFace.glyph().bitmap_top();
				ct.width = scratchBitmap.width();
				ct.height = scratchBitmap.rows();
				if (scratchBitmap.pitch() < 0) {
					ct.flip = true;
				}
				if (ct.width != 0 && ct.height != 0) {
					ct.name = glGenTextures();
					glBindTexture(GL_TEXTURE_2D, ct.name);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
					glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
					glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA8, ct.width, ct.height, 0, GL_ALPHA, GL_UNSIGNED_BYTE, scratchBitmap.buffer(ct.width*ct.height));
				}
				ct.advanceX = ftFace.glyph().advance().x();
				ct.advanceY = ftFace.glyph().advance().y();
				
				cachedTextures.put(key, ct);
			}
			if (ct.name != 0) {
				int minV = 0;
				int maxV = 1;
				
				if (ct.flip) {
					maxV = 0;
					minV = 1;
				}
				
				// only align x and not y to avoid "serial killer letters" effect often seen in e.g. Qt6
				double xo = alignToScreenPixel(x+ct.x/dpiScale);
				double yo = y-(ct.y/dpiScale);
	
				glBindTexture(GL_TEXTURE_2D, ct.name);
				glBegin(GL_QUADS);
					glTexCoord2i(0, minV);
					glVertex2d(xo, yo);
					glTexCoord2i(1, minV);
					glVertex2d(xo+(ct.width/dpiScale), yo);
					glTexCoord2i(1, maxV);
					glVertex2d(xo+(ct.width/dpiScale), yo+(ct.height/dpiScale));
					glTexCoord2i(0, maxV);
					glVertex2d(xo, yo+(ct.height/dpiScale));
				glEnd();
			}
			
			float glyphW = alignToScreenPixel(ct.advanceX/64f/dpiScale);
			totalW += glyphW;
			x += glyphW;
			y += ct.advanceY/64f/dpiScale;
		}
		glDisable(GL_TEXTURE_2D);
		if (dpiScale != 1) {
			glPopMatrix();
		}
		return totalW;
	}

	private float alignToScreenPixel(double x) {
		double newx = Math.round(x*dpiScale)/dpiScale;
		return (float)newx;
	}

	public float measureString(Face f, float size, String str) {
		size = alignToScreenPixel(size);
		int ftSize = (int)(size*64);
		float w = 0;
		for (int i = 0; i < str.length(); i++) {
			FT_Face ftFace = chooseFace(f, str.charAt(i), ftSize);
			if (ftFace == null) continue;
			w += ftFace.glyph().advance().x()/64f;
		}
		return w;
	}
	
	private FT_Face chooseFace(Face f, char ch, int ftSize) {
		FT_Face ftFace = null;
		for (int j = 0; j < f.filenames.length; j++) {
			FT_Face ftFaceTmp = ftFaces.computeIfAbsent(f.filenames[j], this::loadFont);
			FT_Set_Char_Size(ftFaceTmp, 0, ftSize, 72, 72);
			if (FT_Get_Char_Index(ftFaceTmp, ch) != 0) {
				if (FT_Load_Char(ftFaceTmp, ch, FT_LOAD_RENDER) == 0) {
					ftFace = ftFaceTmp;
					break;
				}
			}
		}
		if (ftFace == null) {
			ftFace = ftFaces.computeIfAbsent(f.filenames[0], this::loadFont);
			if (FT_Load_Char(ftFace, '\uFFFD', FT_LOAD_RENDER) != 0) {
				return null;
			}
		}
		return ftFace;
	}

	private FT_Face loadFont(String name) {
		PointerBuffer ftFacePtr = memAllocPointer(1);
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try (InputStream in = GLPuppet.class.getClassLoader().getResourceAsStream("com/unascribed/sup/assets/fonts/"+name)) {
				InputStream win = in;
				if (name.endsWith(".br")) {
					win = new BrotliInputStream(in);
				}
				Util.copy(win, baos);
			} catch (IOException e) {
				Puppet.log("WARN", "Failed to load font "+name, e);
				return null;
			}
			ByteBuffer buf = memAlloc(baos.size());
			buf.put(baos.toByteArray());
			((Buffer)buf).flip();
			int error = FT_New_Memory_Face(ftLibrary, buf, 0, ftFacePtr);
			if (error != 0) {
				Puppet.log("WARN", "Failed to load font "+name+": "+FT_Error_String(error));
				return null;
			}
			return FT_Face.create(ftFacePtr.get(0));
		} finally {
			memFree(ftFacePtr);
		}
	}
	
	private static class CacheKey {
		public final Face face;
		public final float size;
		public final char chr;
		public CacheKey(Face face, float size, char chr) {
			this.face = face;
			this.size = size;
			this.chr = chr;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + chr;
			result = prime * result + ((face == null) ? 0 : face.hashCode());
			result = prime * result + Float.floatToIntBits(size);
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CacheKey other = (CacheKey) obj;
			if (chr != other.chr)
				return false;
			if (face != other.face)
				return false;
			if (Float.floatToIntBits(size) != Float.floatToIntBits(other.size))
				return false;
			return true;
		}
	}
	
	private static class CachedTexture {
		public int name, width, height, x, y;
		public float advanceX, advanceY;
		public boolean flip;
	}
	
}
