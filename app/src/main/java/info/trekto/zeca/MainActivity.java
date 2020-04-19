/*
Copyright 2020 Trayan Momkov

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package info.trekto.zeca;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.PopupMenu;

import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.content.Intent.ACTION_OPEN_DOCUMENT_TREE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static info.trekto.zeca.ImagePreprocessor.convertToBlackAndWhiteCenteredImage;
import static info.trekto.zeca.ImagePreprocessor.getBitmapFromUri;
import static info.trekto.zeca.ImagePreprocessor.getCameraPhotoOrientation;
import static info.trekto.zeca.ImagePreprocessor.getPixelsValues;
import static info.trekto.zeca.ImagePreprocessor.normalize;
import static info.trekto.zeca.ImagePreprocessor.rotateBitmap;
import static info.trekto.zeca.ImagePreprocessor.scaleBitmap;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final DecimalFormat format = new DecimalFormat("0.00");
    private static final int OPEN_PICTURE_PERMISSION_REQUEST_CODE = 28;  // 28 is a perfect number. See Wikipedia.
    private static final int OPEN_IMAGE_REQUEST_CODE = OPEN_PICTURE_PERMISSION_REQUEST_CODE + 1;
    private TextView headerText;
    private ImageView imageView;
    private Classifier classifier;

    /**
     * Load image from uri and classify it using processPhoto(Bitmap bitmap).
     * @param uri Image URI
     */
    private void processPhoto(Uri uri) {
        Bitmap bitmap = getBitmapFromUri(uri, this);
        bitmap = rotateBitmap(bitmap, getCameraPhotoOrientation(uri, this));
        processPhoto(bitmap);
    }

    /**
     * Preprocess image and classify it.
     * @param bitmap Image
     */
    private void processPhoto(Bitmap bitmap) {
        Classification classification = classifier.classify(
                normalize(
                        getPixelsValues(
                                convertToBlackAndWhiteCenteredImage(bitmap))));

        showClassification(classification);
        imageView.setImageBitmap(bitmap);
    }

    /**
     * Show classification on screen.
     * @param classification Contains the recognized digit and confidence.
     */
    private void showClassification(Classification classification) {
        String result = "ZECA";
        if (classification != null) {
            result += "   Digit: " + classification.recognizedDigit
                    + "   Confidence: " + format.format(classification.confidence);
        }
        headerText.setText(result);
    }

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        headerText = findViewById(R.id.header_text);
        imageView = findViewById(R.id.image_view);

        classifier = new Classifier(this);

        InputStream inputStream;
        try {
            inputStream = getAssets().open("e0_0a_receipt_2020-Jan-06_21-56-54-229_11.png");
            processPhoto(BitmapFactory.decodeStream(inputStream));
        } catch (IOException ex) {
            Log.e(TAG, "Cannot open initial image!", ex);
        }
    }

    @Override
    protected void onDestroy() {
        classifier.closeInterpreter();
        super.onDestroy();
    }

    public void showAbout(MenuItem item) {
        startActivityForResult(new Intent(this, AboutActivity.class), 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == OPEN_IMAGE_REQUEST_CODE) {
                if (resultCode == RESULT_OK && data != null) {
                    Uri tempUri = data.getData();
                    if (tempUri != null) {
                        processPhoto(tempUri);
                    } else {
                        showToast(this, "Cannot open photo", Toast.LENGTH_SHORT);
                    }
                }
            }
        } catch (Throwable tr) {
            Log.e(TAG, "Error onActivityResult", tr);
            showToast(this, "Error onActivityResult " + tr.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode == OPEN_PICTURE_PERMISSION_REQUEST_CODE) {
                if (permissionGranted(grantResults)) {
                    open(null);
                } else {
                    showToast(this, "Read permission needed", Toast.LENGTH_LONG);
                }
            }
        } catch (Throwable tr) {
            Log.e(TAG, "Error onRequestPermissionsResult", tr);
            showToast(this, "Error onRequestPermissionsResult " + tr.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }

    public void open(MenuItem item) {
        try {
            if (isExternalStorageReadable(this)) {
                performSearchIntent();
            } else {
                requestPermission(this, READ_EXTERNAL_STORAGE, OPEN_PICTURE_PERMISSION_REQUEST_CODE);
            }
        } catch (Throwable tr) {
            Log.e(TAG, "Error open", tr);
            showToast(this, "Error open: " + tr.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }

    private static void requestPermission(Activity activity, String permission, int requestCode) {
        androidx.core.app.ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
    }

    private static boolean isExternalStorageReadable(Context context) {
        return androidx.core.app.ActivityCompat.checkSelfPermission(context, READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED;
    }

    private void performSearchIntent() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setDataAndType(Uri.parse(getExternalFilesDir(Environment.DIRECTORY_PICTURES).toURI().toString()), "resource/folder");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                intent.putExtra(ACTION_OPEN_DOCUMENT_TREE, getExternalFilesDir(Environment.DIRECTORY_PICTURES));
            }

            startActivityForResult(intent, OPEN_IMAGE_REQUEST_CODE);
        } catch (Throwable tr) {
            Log.e(TAG, "Error performSearchIntent", tr);
            showToast(this, "Error performSearchIntent: " + tr.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }

    public static void showToast(final Activity activity, final String message, final int period) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(activity, message, period).show();
            }
        });
    }

    private static boolean permissionGranted(int[] grantResults) {
        return grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED;
    }

    private void populateMenu(Menu menu) throws IOException {
        AssetManager assetManager = getAssets();
        int itemNumber = 2; // For Open and About menu items
        for(final String filename : assetManager.list("")) {
            if (itemNumber >= menu.size()) {
                break;
            }
            if (filename.endsWith(".png")) {
                final Bitmap bitmap = BitmapFactory.decodeStream(assetManager.open(filename));
                Drawable icon = new BitmapDrawable(getResources(), scaleBitmap(bitmap, 64, true));
                menu.getItem(itemNumber).setIcon(icon);
                menu.getItem(itemNumber++).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        processPhoto(bitmap);
                        return false;
                    }
                });
            }
        }
    }

    @SuppressLint("RestrictedApi")
    public void showMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.menu_main, popup.getMenu());

        try {
            populateMenu(popup.getMenu());
        } catch (IOException ex) {
            Log.e(TAG, "Cannot populateMenu", ex);
        }

        // For icons
        if (popup.getMenu() instanceof MenuBuilder) {
            MenuBuilder menuBuilder = (MenuBuilder) popup.getMenu();
            menuBuilder.setOptionalIconsVisible(true);
        }
        popup.show();
    }
}
