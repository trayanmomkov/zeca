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

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.nio.MappedByteBuffer;

import static info.trekto.zeca.MainActivity.showToast;
import static org.tensorflow.lite.support.common.FileUtil.loadMappedFile;

class Classifier {
    private static final String TAG = "Classifier";

    private Interpreter interpreter;

    Classifier(Activity activity) {
        try {
            MappedByteBuffer model = loadMappedFile(activity, "2020-Mar-31_20-03-28_LATENCY_antialiasing_B-W.tflite");
            interpreter = new Interpreter(model, new Interpreter.Options());
        } catch (IOException ex) {
            Log.e(TAG, "Cannot load tflite model!", ex);
            showToast(activity, "Cannot load tflite model: " + ex.getLocalizedMessage(), Toast.LENGTH_LONG);
        }
    }

    Classification classify(float[] pixels) {
        if (interpreter == null) {
            return null;
        } else {
            // Our classifier is binary. We have only two classes.
            // Thus the output is just a single number - the probability the digit to be eight.
            // If the probability is above 0.5 we assume the digit is eight. Otherwise - zero.
            float[][] output = new float[1][1];

            interpreter.run(pixels, output);

            float result = output[0][0];
            char digit = result > 0.5f ? '8' : '0';
            return new Classification(digit, calculateConfidence(result));
        }
    }

    /**
     * Calculates how confident we are that the classified digit is correct.
     * @param result The result of the binary classification.
     * @return Confidence between 0 and 1 inclusive.
     */
    private float calculateConfidence(float result) {
        if (result > 0.5f) {
            return 1-2*(1-result);
        } else {
            return 1-2*(result);
        }
    }

    void closeInterpreter() {
        if (interpreter != null) {
            interpreter.close();
        }
    }
}
