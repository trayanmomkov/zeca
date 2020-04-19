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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

import static android.graphics.Bitmap.createBitmap;
import static android.graphics.Color.BLACK;
import static android.graphics.Color.WHITE;
import static android.media.ExifInterface.ORIENTATION_NORMAL;
import static android.media.ExifInterface.TAG_ORIENTATION;

class ImagePreprocessor {
    private static final String TAG = "ImagePreprocessor";

    private static final int IMAGE_W = 32;
    private static final int IMAGE_H = 32;
    private static final int TOTAL_PIXELS = IMAGE_W * IMAGE_H;

    /**
     * 1. Convert to monochrome
     * 2. Scale antialiasing
     * 3. Convert to black and white
     * 4. Add fixed frame
     * 5. Center image in the given frame
     *
     * @param original Original image.
     * @return Black and white image centered within a fixed frame.
     */
    static Bitmap convertToBlackAndWhiteCenteredImage(Bitmap original) {
        Bitmap tempBitmap = convertToMonochrome(original);
        tempBitmap = scaleBitmap(tempBitmap, IMAGE_W, true);
        convertToBlackAndWhite(tempBitmap, findBlackAndWhiteAverage(tempBitmap));
        tempBitmap = addFrame(tempBitmap, IMAGE_W, IMAGE_H);
        tempBitmap = centerImage(tempBitmap);
        return tempBitmap;
    }

    /**
     * Center image based on:
     * - its center of mass for x coordinate;
     * - bounding box (frame) for y coordinate.
     *
     * @param bitmap Image to be centered.
     * @return Centered image
     */
    private static Bitmap centerImage(Bitmap bitmap) {
        int totalMassX = 0;
        int top = IMAGE_H;
        int bottom = 0;
        int blackPixelsCount = 0;
        int totalBlackPixelsMass = 0;
        float meanColorValue = 0;

        int[] pixels = new int[TOTAL_PIXELS];
        bitmap.getPixels(pixels, 0, IMAGE_W, 0, 0, IMAGE_W, IMAGE_H);

        for (int x = 0; x < IMAGE_W; x++) {
            for (int y = 0; y < IMAGE_H; y++) {
                meanColorValue += Color.red(pixels[y * IMAGE_W + x]);
            }
        }

        meanColorValue /= (float) TOTAL_PIXELS;

        for (int x = 0; x < IMAGE_W; x++) {
            for (int y = 0; y < IMAGE_H; y++) {
                int pixel = pixels[y * IMAGE_W + x];
                int r = Color.red(pixel);
                int b = Color.blue(pixel);
                int g = Color.green(pixel);
                if (r != g || r != b) {
                    String msg = "Not monochrome image: " + r + " " + g + " " + b;
                    Log.e(TAG, msg);
                }

                // If dark enough
                if (r < meanColorValue) {
                    int mass = (255 - r);
                    totalMassX += mass * x;
                    totalBlackPixelsMass += mass;
                    blackPixelsCount++;

                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }

        float meanBlackPixelMass = totalBlackPixelsMass / (float) blackPixelsCount;
        int centerWeightX = Math.round(totalMassX / meanBlackPixelMass / (float) blackPixelsCount);
        int centerFormY = Math.round(top + (bottom - top) / 2f);

        int[] newPixels = move(pixels, IMAGE_W / 2 - centerWeightX, IMAGE_H / 2 - centerFormY);
        Bitmap centeredBitmap = Bitmap.createBitmap(IMAGE_W, IMAGE_H, bitmap.getConfig());
        centeredBitmap.setPixels(newPixels, 0, IMAGE_W, 0, 0, IMAGE_W, IMAGE_H);
        return centeredBitmap;
    }

    /**
     * Move image with diff_x and diff_y from the center.
     *
     * @param pixels Pixels represent the image.
     * @param diff_x X difference from center.
     * @param diff_y Y difference from center.
     * @return Centered image.
     */
    private static int[] move(int[] pixels, int diff_x, int diff_y) {
        int[] new_pixels = new int[TOTAL_PIXELS];

        for (int x = 0; x < IMAGE_W; x++) {
            for (int y = 0; y < IMAGE_H; y++) {
                int oldX = x - diff_x;
                int oldY = y - diff_y;

                if (oldX < 0 || oldX >= IMAGE_W || oldY < 0 || oldY >= IMAGE_H) {
                    new_pixels[y * IMAGE_W + x] = Color.WHITE;
                } else {
                    new_pixels[y * IMAGE_W + x] = pixels[oldY * IMAGE_W + oldX];
                }
            }
        }

        return new_pixels;
    }

    /**
     * Calculate average pixel colour.
     *
     * @param bitmap Must be a monochrome bitmap (greyscale image).
     * @return Average pixel colour.
     */
    private static int findBlackAndWhiteAverage(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        long total = 0;

        for (int pixel : pixels) {
            total += pixel & 0xff;
        }

        return Math.round(total / (float) pixels.length);
    }

    /**
     * All pixels darker than threshold become black.
     * All pixels brighter then or equal to threshold become white.
     *
     * @param bitmap    The image.
     * @param threshold Black and white threshold.
     */
    private static void convertToBlackAndWhite(Bitmap bitmap, int threshold) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int lowestBit = pixels[i] & 0xff;
            if (lowestBit < threshold)
                pixels[i] = BLACK;
            else
                pixels[i] = WHITE;
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private static Bitmap convertToMonochrome(Bitmap bitmap) {
        Bitmap monochromeBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
        Canvas canvas = new Canvas(monochromeBitmap);
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(matrix));
        canvas.drawBitmap(bitmap, 0, 0, paint);

        return monochromeBitmap;
    }

    /**
     * Add frame around image.
     *
     * @param bitmap      The image.
     * @param finalWidth  Desired width after adding a frame.
     * @param finalHeight Desired height after adding a frame.
     * @return The image with frame.
     */
    private static Bitmap addFrame(Bitmap bitmap, int finalWidth, int finalHeight) {
        Bitmap bitmapWithBorder = Bitmap.createBitmap(finalWidth, finalHeight, bitmap.getConfig());
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        Canvas canvas = new Canvas(bitmapWithBorder);
        canvas.drawColor(WHITE);
        canvas.drawBitmap(bitmap, (finalWidth - bitmap.getWidth()) / 2f, (finalHeight - bitmap.getHeight()) / 2f, paint);
        return bitmapWithBorder;
    }

    /**
     * Resize image.<p>
     * 600x200 bitmap and maxLengthPixels = 100 will become 100x33,33. 200/(600/100) = 33,33(3)<p>
     * 200x600 and maxLengthPixels = 100 will become 33,33x100<p>
     *
     * @param bitmap          the image.
     * @param maxLengthPixels Max size of either width or height.
     * @param antialiasing    If true the scaled image is smooth. If false - pixelate.
     * @return scaled bitmap
     */
    static Bitmap scaleBitmap(Bitmap bitmap, int maxLengthPixels, boolean antialiasing) {
        if (bitmap == null) {
            return null;
        }

        float scaleFactor = Math.max(
                bitmap.getWidth() / (float) maxLengthPixels,
                bitmap.getHeight() / (float) maxLengthPixels);

        Bitmap resizedBitmap =
                Bitmap.createScaledBitmap(
                        bitmap,
                        (int) (bitmap.getWidth() / scaleFactor),
                        (int) (bitmap.getHeight() / scaleFactor),
                        antialiasing);

        return resizedBitmap;
    }

    static Bitmap getBitmapFromUri(Uri uri, Context context) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    context.getContentResolver().openFileDescriptor(uri, "r");
            if (parcelFileDescriptor == null) {
                throw new RuntimeException("Cannot open file");
            }
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            if (image == null) {
                throw new RuntimeException("Cannot decode bitmap");
            }
            parcelFileDescriptor.close();
            return image;
        } catch (Throwable tr) {
            throw new RuntimeException("Cannot open file");
        }
    }

    static int getCameraPhotoOrientation(Uri imageUri, Context context) {
        switch (getExifOrientation(imageUri, context)) {
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            default:
                return 0;
        }
    }

    private static int getExifOrientation(Uri uri, Context context) {
        InputStream inputStream = null;
        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Cannot get EXIF orientation of null inputStream");
                return ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(inputStream);
            return exif.getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL);
        } catch (IOException ex) {
            Log.e(TAG, "Cannot get exif data", ex);
            return 0;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Cannot close inputStream", ex);
                }
            }
        }
    }

    static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    /**
     * Get the values from the bitmap pixels.
     *
     * @param bitmap A monochrome image.
     * @return Array with integers between [0, 255]
     */
    static int[] getPixelsValues(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                pixels[x * width + y] = bitmap.getPixel(x, y) & 0xff;
            }
        }

        return pixels;
    }

    /**
     * Divide the numbers in input by 255 to normalize them in interval [0, 1]
     *
     * @param input Integers between [0, 255]
     * @return Floats between [0, 1]
     */
    static float[] normalize(int[] input) {
        float[] normalized = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            normalized[i] = input[i] / 255f;
        }

        return normalized;
    }
}
