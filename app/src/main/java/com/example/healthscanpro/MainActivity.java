package com.example.healthscanpro;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.android.material.card.MaterialCardView;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private TextView resultText, brandText, ingredientsText;
    private Button scanBtn;
    private ImageView productImage;
    private MaterialCardView productCard;
    private OkHttpClient client = new OkHttpClient();

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result != null && result.getContents() != null) {
                    String code = result.getContents();
                    resultText.setText("Scanned: " + code);
                    fetchProductDetails(code);
                } else {
                    resultText.setText("Scan cancelled or no result.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.resultText);
        brandText = findViewById(R.id.brandText);
        ingredientsText = findViewById(R.id.ingredientsText);
        productImage = findViewById(R.id.productImage);
        productCard = findViewById(R.id.productCard);
        scanBtn = findViewById(R.id.scanBtn);

        scanBtn.setOnClickListener(v -> startBarcodeScanner());
    }

    private void startBarcodeScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Place a barcode inside the viewfinder to scan");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(CaptureAct.class);
        barcodeLauncher.launch(options);
    }

    private void fetchProductDetails(String barcode) {
        new Thread(() -> {
            String url = "https://world.openfoodfacts.org/api/v0/product/" + barcode + ".json";
            Request request = new Request.Builder().url(url).build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    JsonObject product = root.getAsJsonObject("product");

                    String brand = product.has("brands") ? product.get("brands").getAsString() : "N/A";
                    String ingredients = product.has("ingredients_text") ?
                            product.get("ingredients_text").getAsString() : "N/A";
                    String imageUrl = product.has("image_url") ? product.get("image_url").getAsString() : null;

                    runOnUiThread(() -> {
                        brandText.setText(brand);
                        ingredientsText.setText(ingredients);

                        // Load image manually without Glide
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            new LoadImageTask(productImage).execute(imageUrl);
                        } else {
                            productImage.setImageResource(R.drawable.ic_placeholder);
                        }

                        // Animate the product card
                        productCard.setAlpha(0f);
                        productCard.setTranslationX(300f);
                        productCard.animate()
                                .alpha(1f)
                                .translationX(0f)
                                .setDuration(500)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    brandText.setText("Error");
                    ingredientsText.setText("Could not fetch");
                    productImage.setImageResource(R.drawable.ic_placeholder);
                });
            }
        }).start();
    }

    // AsyncTask to load image from URL
    private static class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;

        LoadImageTask(ImageView imageView) {
            this.imageView = imageView;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                return BitmapFactory.decodeStream(input);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            } else {
                imageView.setImageResource(R.drawable.ic_placeholder);
            }
        }
    }
}
