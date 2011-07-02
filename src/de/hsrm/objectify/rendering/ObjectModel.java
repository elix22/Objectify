package de.hsrm.objectify.rendering;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import de.hsrm.objectify.R;
import de.hsrm.objectify.utils.ExternalDirectory;

/**
 * A representation of an actual object. Vertices, normals and texture can be
 * added after an instance of this class has been made. This class implements
 * {@link Parcelable} for sending between different Activities.
 * 
 * @author kwolf001
 * 
 */
public class ObjectModel implements Parcelable {

	private static final String TAG = "ObjectModel";
	private FloatBuffer vertexBuffer;
	private FloatBuffer textureBuffer;
	private FloatBuffer normalBuffer;
	private ShortBuffer facesBuffer;
	
	private int[] textures = new int[1];
	
	private float[] texture;
	private float vertices[];
	private float normals[];
	private short faces[];
	private Bitmap image;
	private String image_suffix;
	
	public ObjectModel(float[] vertices, float[] normals, short[] faces,
			Bitmap image, String image_suffix) {
		
		setVertices(vertices);
		setNormalVertices(normals);
		setFaces(faces);
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(vertices.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		vertexBuffer = byteBuf.asFloatBuffer();
		vertexBuffer.put(vertices);
		vertexBuffer.rewind();
		
		byteBuf = ByteBuffer.allocateDirect(normals.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		normalBuffer = byteBuf.asFloatBuffer();
		normalBuffer.put(normals);
		normalBuffer.rewind();
		
		byteBuf = ByteBuffer.allocateDirect(faces.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		facesBuffer = byteBuf.asShortBuffer();
		facesBuffer.put(faces);
		facesBuffer.rewind();
		
		calcTextureCoords(image);
		byteBuf = ByteBuffer.allocateDirect(texture.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		textureBuffer = byteBuf.asFloatBuffer();
		textureBuffer.put(texture);
		textureBuffer.rewind();
		
		this.image_suffix = image_suffix;
		this.image = Bitmap.createBitmap(image);
	}
	
	private ObjectModel(Parcel source) {
		Bundle b = source.readBundle();
		textures = new int[1];
		setVertices(b.getFloatArray("vertices"));
		setNormalVertices(b.getFloatArray("normals"));
		setFaces(b.getShortArray("faces"));
		this.image_suffix = b.getString("image_suffix");
		String path = ExternalDirectory.getExternalImageDirectory()+"/"+this.image_suffix+"_1.png";
		this.image = BitmapFactory.decodeFile(path);
		
		ByteBuffer byteBuf = ByteBuffer.allocateDirect(vertices.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		vertexBuffer = byteBuf.asFloatBuffer();
		vertexBuffer.put(vertices);
		vertexBuffer.rewind();
		
		byteBuf = ByteBuffer.allocateDirect(normals.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		normalBuffer = byteBuf.asFloatBuffer();
		normalBuffer.put(normals);
		normalBuffer.rewind();
		
		byteBuf = ByteBuffer.allocateDirect(faces.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		facesBuffer = byteBuf.asShortBuffer();
		facesBuffer.put(faces);
		facesBuffer.rewind();
		
		calcTextureCoords(this.image);
		byteBuf = ByteBuffer.allocateDirect(texture.length * 4);
		byteBuf.order(ByteOrder.nativeOrder());
		textureBuffer = byteBuf.asFloatBuffer();
		textureBuffer.put(texture);
		textureBuffer.rewind();
	}
	
	private void calcTextureCoords(Bitmap image) {
		int width = image.getWidth();
		int height = image.getHeight();
		ArrayList<Float> textcoords = new ArrayList<Float>();
		for (int x=0; x<height; x++) {
			for (int y=0; y<width; y++) {
				float idx = (1.0f / (width-1)) * y;
				float idy = (1.0f / (height-1)) * x;
				textcoords.add(idx);
				textcoords.add(idy);
			}
		}
		this.texture = new float[textcoords.size()];
		for (int i=0; i<textcoords.size(); i++) {
			texture[i] = textcoords.get(i);
		}
	}

	public void setVertices(float[] verts) {
		this.vertices = new float[verts.length];
		System.arraycopy(verts, 0, this.vertices, 0, verts.length);
		setVertexBuffer(this.vertices);
	}
	
	public float[] getVertices() {
		return this.vertices;
	}
	
	public void setNormalVertices(float[] nverts) {
		this.normals = new float[nverts.length];
		System.arraycopy(nverts, 0, this.normals, 0, nverts.length);
		setNormalBuffer(this.normals);
	}
	
	public float[] getNormalVertices() {
		return this.normals;
	}
	
	public void setFaces(short[] face) {
		this.faces = new short[face.length];
		System.arraycopy(face, 0, faces, 0, face.length);
		setFacesBuffer(this.faces);
	}
	
	public short[] getFaces() {
		return this.faces;
	}
	
	public void setTextures(int[] textures) {
		this.textures = new int[textures.length];
		System.arraycopy(textures, 0, this.textures, 0, textures.length);
	}
	
	public int[] getTextures() {
		return this.textures;
	}
	
	public String getImageSuffix() {
		return image_suffix;
	}
	
	private void setVertexBuffer(float[] vertices) {
		ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length * 4);
		vbb.order(ByteOrder.nativeOrder());
		this.vertexBuffer = vbb.asFloatBuffer();
		for (float f : vertices) {
			vertexBuffer.put(f);
		}
		vertexBuffer.rewind();
	}
	
	private void setNormalBuffer(float[] normal) {
		ByteBuffer nbb = ByteBuffer.allocateDirect(normal.length * 4);
		nbb.order(ByteOrder.nativeOrder());
		this.normalBuffer = nbb.asFloatBuffer();
		for (float f : normals) {
			normalBuffer.put(f);
		}
		normalBuffer.rewind();
	}
	
	private void setFacesBuffer(short[] faces) {
		ByteBuffer fbb = ByteBuffer.allocateDirect(faces.length * 2);
		fbb.order(ByteOrder.nativeOrder());
		facesBuffer = fbb.asShortBuffer();
		for (short s : faces) {
			facesBuffer.put(s);
		}
		facesBuffer.rewind();
	}
	
	public void draw(GL10 gl) {
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);
		
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL10.GL_NORMAL_ARRAY);
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		
		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);
		gl.glNormalPointer(GL10.GL_FLOAT, 0, normalBuffer);
		gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textureBuffer);
		
		gl.glDrawElements(GL10.GL_TRIANGLES, faces.length, GL10.GL_UNSIGNED_SHORT, facesBuffer);
		
		gl.glDisableClientState(GL10.GL_NORMAL_ARRAY);
		gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
	}

	/**
	 * Loading texture
	 * 
	 * @param gl
	 *            the GL Context
	 */
	public void loadGLTexture(GL10 gl, Context context) {
		Bitmap texture = scaleTexture(image, 256);

		gl.glGenTextures(1, textures, 0);
		gl.glBindTexture(GL10.GL_TEXTURE_2D, textures[0]);

		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER,GL10.GL_NEAREST);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER,GL10.GL_LINEAR);

		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S,GL10.GL_REPEAT);
		gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T,GL10.GL_REPEAT);

		GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, texture, 0);

		texture.recycle();
	}
	
	/**
	 * Scales image to valid texture
	 * 
	 * @param image
	 *            original image
	 * @param size
	 *            preferred width and height size
	 * @return scaled image
	 */
	private Bitmap scaleTexture(Bitmap image, int size) {
		int width = image.getWidth();
		int height = image.getHeight();
		float scaleWidth = ((float) size) / width;
		float scaleHeight = ((float) size) / height;
		Matrix matrix = new Matrix();
		matrix.postScale(scaleWidth, scaleHeight);
		return Bitmap.createBitmap(image, 0, 0, width, height, matrix, true);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel out, int flags) {
		Bundle b = new Bundle();
		b.putFloatArray("vertices", vertices);
		b.putFloatArray("normals", normals);
		b.putShortArray("faces", faces);
		b.putString("image_suffix", image_suffix);
		out.writeBundle(b);
	}
	
	public static final Parcelable.Creator<ObjectModel> CREATOR = new Creator<ObjectModel>() {
		
		@Override
		public ObjectModel[] newArray(int size) {
			return new ObjectModel[size];
		}
		
		@Override
		public ObjectModel createFromParcel(Parcel source) {
			return new ObjectModel(source);
		}
	};

}
