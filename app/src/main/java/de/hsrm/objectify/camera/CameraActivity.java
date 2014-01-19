package de.hsrm.objectify.camera;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Calendar;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import de.hsrm.objectify.R;
import de.hsrm.objectify.SettingsActivity;
import de.hsrm.objectify.database.DatabaseAdapter;
import de.hsrm.objectify.database.DatabaseProvider;
import de.hsrm.objectify.math.Matrix;
import de.hsrm.objectify.math.Vector3f;
import de.hsrm.objectify.math.VectorNf;
import de.hsrm.objectify.rendering.Circle;
import de.hsrm.objectify.rendering.ObjectModel;
import de.hsrm.objectify.rendering.ObjectViewerActivity;
import de.hsrm.objectify.utils.BitmapUtils;
import de.hsrm.objectify.utils.ExternalDirectory;
import de.hsrm.objectify.utils.Image;
import de.hsrm.objectify.utils.MathHelper;

/**
 * This {@link Activity} shoots photos with the front facing camera, manages
 * light settings with {@link CameraLighting} and calculates the normal- and
 * height map from the given photos.
 * 
 * @author kwolf001
 * 
 */
public class CameraActivity extends Activity {

	private String TAG = "CameraActivity";
	private CameraPreview cameraPreview;
	private Button triggerPictures;
	private LinearLayout progress;
	private CameraLighting cameraLighting;
	private ArrayList<Image> pictureList;
	private int numberOfPictures;
	private int counter = 0;
	private Context context;
	private Camera camera;
	private Image texture;
	public static Runnable shootPicture;
	public static Handler handler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.camera);
		context = this;
		setScreenBrightness(1);

		cameraPreview = (CameraPreview) findViewById(R.id.camera_surface);
		cameraLighting = (CameraLighting) findViewById(R.id.camera_lighting);
		cameraLighting.setZOrderOnTop(true);
		progress = (LinearLayout) findViewById(R.id.progress);
		triggerPictures = (Button) findViewById(R.id.trigger_picture_button);
		triggerPictures.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				/* Fetching number of pictures to shoot from the settings */
				ContextWrapper contextWrapper = new ContextWrapper(context);
				SharedPreferences prefs = SettingsActivity
						.getSettings(contextWrapper);
				numberOfPictures = prefs.getInt(
						getString(R.string.settings_amount_pictures), 4);
				/* Setting the trigger button to invisible */
				triggerPictures.setVisibility(View.GONE);
				/* New ArrayList for storing the pictures temporarily */
				pictureList = new ArrayList<Image>();
				setLights();
			}
		});

		camera = CameraFinder.INSTANCE.open(context);
		if (camera == null) {
			showToastAndFinish(getString(R.string.no_ffc_was_found));
		} else {
			cameraPreview.setCamera(camera);
		}

		handler = new Handler();
		shootPicture = new Runnable() {

			@Override
			public void run() {
				camera.startPreview();
				camera.takePicture(null, null, jpegCallback());
			}
		};

	}

	@Override
	protected void onPause() {
		super.onPause();
		setScreenBrightness(-1);
		((Activity) context).finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		setScreenBrightness(1);
	}

	/**
	 * Set screen brightness. A value of less than 0, the default, means to use
	 * the preferred screen brightness. 0 to 1 adjusts the brightness from dark
	 * to full bright.
	 * 
	 * @param intensity
	 *            value between 0 and 1
	 */
	private void setScreenBrightness(float intensity) {
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		lp.screenBrightness = intensity;
		getWindow().setAttributes(lp);
	}

	/**
	 * Will be called when no front facing camera was found.
	 * 
	 * @param message
	 *            message which will be displayed
	 */
	private void showToastAndFinish(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
		finish();
	}

	/**
	 * Displays a white {@link Circle} on the display for lighting up different
	 * parts of the object. As a standard behavior this function looks up the
	 * number of pictures which will be taken and moves the lighting source in a
	 * circle around the display.
	 */
	private void setLights() {
		cameraLighting.setVisibility(View.VISIBLE);
		cameraLighting.setZOrderOnTop(true);
		cameraLighting.putLightSource(numberOfPictures, counter);
	}

	private PictureCallback jpegCallback() {
		PictureCallback callback = new PictureCallback() {
			@Override
			public void onPictureTaken(byte[] data, Camera camera) {
				Image image = new Image(BitmapUtils.createScaledBitmap(data,
						CameraFinder.pictureSize, CameraFinder.imageFormat),
						true);

				pictureList.add(image);
				counter += 1;

				if (counter == numberOfPictures) {
					new CalculateModel().execute();
				} else {
					setLights();
				}
			}
		};

		return callback;
	}

	/**
	 * Calculates <a
	 * href="http://en.wikipedia.org/wiki/Normal_mapping">normalmap</a> and <a
	 * href="http://en.wikipedia.org/wiki/Heightmap">heightmap</a> from shot
	 * photos. The parameters for the AsyncTask are:
	 * <ul>
	 * <li>Void: all pictures will be held by the {@link CameraActivity}.</li>
	 * <li>Void: We don't need to update any progress by now</li>
	 * <li>Boolean: Indicating whether we were successful calculating an object</li>
	 * </ul>
	 * 
	 * @author kwolf001
	 * 
	 */
	private class CalculateModel extends AsyncTask<Void, Void, Boolean> {

		private ObjectModel objectModel;
		private ContentResolver cr;
		private boolean sdIsMounted = true;
		private boolean useBlurring = false;

		@Override
		protected void onPreExecute() {
			progress.setVisibility(View.VISIBLE);
			cameraLighting.setVisibility(View.GONE);
			cameraLighting.setZOrderOnTop(false);
			cr = getContentResolver();
			SharedPreferences settings = SettingsActivity
					.getSettings((ContextWrapper) context);
			useBlurring = settings.getBoolean(
					getString(R.string.settings_use_blurring), false);
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			Matrix sMatrix = cameraLighting.getLightMatrixS(numberOfPictures);
			Matrix sInverse = sMatrix.pseudoInverse();

			int imageWidth = pictureList.get(0).getWidth();
			int imageHeight = pictureList.get(0).getHeight();

			/* sum images and use as texture */
			texture = BitmapUtils.imagesToStack(pictureList);

			/* adjust contrast automagically */
			texture = BitmapUtils.autoContrast(texture);
			// texture = BitmapUtils.modAutoContrast(texture);

			/* equalize histogram for more saturated color */
			// texture = BitmapUtils.equalizeHistogram(texture);

			/* blur the input images */
			if (useBlurring) {
				for (int i = 0; i < pictureList.size(); i++) {
					pictureList.set(i,
							BitmapUtils.blurBitmap(pictureList.get(i)));
				}
			}
			ArrayList<Vector3f> normalField = new ArrayList<Vector3f>();
			Matrix pGradients = new Matrix(imageHeight, imageWidth);
			Matrix qGradients = new Matrix(imageHeight, imageWidth);

			for (int h = 0; h < imageHeight; h++) {
				for (int w = 0; w < imageWidth; w++) {
					Vector3f normal = new Vector3f();
					VectorNf intensity = new VectorNf(numberOfPictures);
					for (int i = 0; i < numberOfPictures; i++) {
						intensity.set(i, pictureList.get(i).getIntensity(w, h));
					}
					Vector3f albedo = sInverse.multiply(intensity);

					float reg = (float) Math.sqrt(Math.pow(albedo.x, 2)
							+ Math.pow(albedo.y, 2) + Math.pow(albedo.z, 2));
					normal.x = albedo.x / reg;
					normal.y = albedo.y / reg;
					normal.z = albedo.z / reg;

					normalField.add(normal);
					pGradients.set(h, w, normal.x / normal.z);
					qGradients.set(h, w, normal.y / normal.z);
				}
			}

			double[][] heightField = MathHelper.twoDimIntegration(pGradients,
					qGradients, imageHeight, imageWidth);

			FloatBuffer vertBuffer = FloatBuffer.allocate(imageHeight
					* imageWidth * 3);
			FloatBuffer normBuffer = FloatBuffer.allocate(imageHeight
					* imageWidth * 3);
			ArrayList<Short> indexes = new ArrayList<Short>();
			vertBuffer.rewind();
			normBuffer.rewind();
			// Vertices und Normale
			int idx = 0;
			for (int x = 0; x < imageHeight; x++) {
				for (int y = 0; y < imageWidth; y++) {
					float[] imgPoint = new float[] { Float.valueOf(y),
							Float.valueOf(x), (float) heightField[x][y] };
					float[] normVec = new float[] { normalField.get(idx).x,
							normalField.get(idx).y, normalField.get(idx).z };
					vertBuffer.put(imgPoint);
					normBuffer.put(normVec);
					idx += 1;
				}
			}
			// Faces
			for (int i = 0; i < imageHeight - 1; i++) {
				for (int j = 0; j < imageWidth - 1; j++) {
					short index = (short) (j + (i * imageWidth));
					indexes.add((short) (index));
					indexes.add((short) (index + imageWidth));
					indexes.add((short) (index + 1));

					indexes.add((short) (index + 1));
					indexes.add((short) (index + imageWidth));
					indexes.add((short) (index + imageWidth + 1));
				}
			}
			ShortBuffer indexBuffer = ShortBuffer.allocate(indexes.size());
			indexBuffer.rewind();
			for (int i = 0; i < indexes.size(); i++) {
				indexBuffer.put(indexes.get(i));
			}

			float[] vertices = new float[vertBuffer.limit()];
			float[] normals = new float[normBuffer.limit()];
			short[] faces = new short[indexBuffer.limit()];
			vertices = vertBuffer.array();
			normals = normBuffer.array();
			faces = indexBuffer.array();

			objectModel = new ObjectModel(vertices, normals, faces, texture);

			if (ExternalDirectory.isMounted()) {
				// storing the newly created 3D object onto hard disk and create
				// database entries
				Calendar cal = Calendar.getInstance();
				String timestamp = String.valueOf(cal.getTimeInMillis());
				String filename = timestamp + ".kaw";
				String path = ExternalDirectory.getExternalImageDirectory()
						+ "/";
				ContentValues values = new ContentValues();
				cr = getContentResolver();
				Uri objectUri = DatabaseProvider.CONTENT_URI.buildUpon()
						.appendPath("object").build();
				Uri galleryUri = DatabaseProvider.CONTENT_URI.buildUpon()
						.appendPath("gallery").build();
				try {
					OutputStream out = new FileOutputStream(path + filename);
					ObjectOutputStream obj_output = new ObjectOutputStream(out);
					obj_output.writeObject(objectModel);
					obj_output.close();
					values.put(DatabaseAdapter.OBJECT_FILE_PATH_KEY, path
							+ filename);
					Uri objectResultUri = cr.insert(objectUri, values);
					String objectID = objectResultUri.getLastPathSegment();
					values.clear();
					String thumbnail_path = ExternalDirectory
							.getExternalImageDirectory()
							+ "/"
							+ timestamp
							+ ".png";
					FileOutputStream fos = new FileOutputStream(thumbnail_path);
					BufferedOutputStream bos = new BufferedOutputStream(fos);
					texture.rotate(-90);
					texture.compress(CompressFormat.PNG, 100, bos);
					bos.flush();
					bos.close();
					values.put(DatabaseAdapter.GALLERY_THUMBNAIL_PATH_KEY,
							thumbnail_path);
					values.put(DatabaseAdapter.GALLERY_NUMBER_OF_PICTURES_KEY,
							String.valueOf(numberOfPictures));
					values.put(DatabaseAdapter.GALLERY_DATE_KEY, timestamp);
					values.put(DatabaseAdapter.GALLERY_DIMENSION_KEY,
							objectModel.getTextureBitmapSize());
					values.put(DatabaseAdapter.GALLERY_FACES_KEY, String
							.valueOf(objectModel.getVertices().length / 3));
					values.put(DatabaseAdapter.GALLERY_VERTICES_KEY,
							String.valueOf(objectModel.getVertices().length));
					values.put(DatabaseAdapter.GALLERY_OBJECT_ID_KEY, objectID);
					cr.insert(galleryUri, values);
				} catch (FileNotFoundException e) {
					Log.e(TAG, e.getLocalizedMessage());
					e.printStackTrace();
					return false;
				} catch (IOException e) {
					Log.e(TAG, e.getLocalizedMessage());
					e.printStackTrace();
					return false;
				}
			} else {
				sdIsMounted = false;
			}
			return true;
		}

		@Override
		protected void onPostExecute(Boolean createdSuccessfully) {
			if (createdSuccessfully) {
				if (!sdIsMounted) {
					Toast.makeText(context,
							getString(R.string.obj_could_not_be_saved),
							Toast.LENGTH_SHORT).show();
				}
				Intent viewObject = new Intent(context,
						ObjectViewerActivity.class);
				Bundle b = new Bundle();
				b.putParcelable("objectModel", objectModel);
				viewObject.putExtra("bundle", b);
				startActivity(viewObject);
				((Activity) context).finish();
			} else {
				Toast.makeText(context,
						getString(R.string.error_while_creating_object),
						Toast.LENGTH_LONG).show();
				((Activity) context).finish();
			}

		}

	}

}