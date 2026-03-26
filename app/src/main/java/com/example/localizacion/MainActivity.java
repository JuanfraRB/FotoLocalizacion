package com.example.localizacion;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.ViewModelProvider;

import com.example.localizacion.databinding.ActivityMainBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private CompassViewModel compassViewModel;
    private File photoFile;
    private float currentAzimuth = 0f;
    private Bitmap markedBitmap;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    checkGpsAndTakePick();
                } else {
                    Toast.makeText(this, R.string.permiso_denegado, Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    processCapturedPhoto();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SplashFragment())
                    .commit();
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        // Uso de la API no obsoleta de ViewModelProvider
        compassViewModel = new ViewModelProvider(this).get(CompassViewModel.class);

        compassViewModel.azimuth.observe(this, azimuth -> currentAzimuth = azimuth);

        binding.btnHacerFoto.setOnClickListener(v -> {
            if (checkPermissions()) {
                checkGpsAndTakePick();
            } else {
                requestRequiredPermissions();
            }
        });

        binding.btnAbout.setOnClickListener(v -> getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
                .add(android.R.id.content, new AboutFragment())
                .addToBackStack(null)
                .commit());

        binding.btnRepetir.setOnClickListener(v -> {
            binding.layoutPreview.setVisibility(View.GONE);
            binding.btnHacerFoto.setVisibility(View.VISIBLE);
            binding.btnAbout.setVisibility(View.VISIBLE);
            checkGpsAndTakePick();
        });

        binding.btnGuardar.setOnClickListener(v -> {
            if (markedBitmap != null) {
                saveImageToGallery(markedBitmap);
                binding.layoutPreview.setVisibility(View.GONE);
                binding.btnHacerFoto.setVisibility(View.VISIBLE);
                binding.btnAbout.setVisibility(View.VISIBLE);
                markedBitmap = null;
            }
        });

        // Configuración correcta del dispatcher para evitar recursividad y mensajes de deprecado
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else if (binding.layoutPreview.getVisibility() == View.VISIBLE) {
                    binding.layoutPreview.setVisibility(View.GONE);
                    binding.btnHacerFoto.setVisibility(View.VISIBLE);
                    binding.btnAbout.setVisibility(View.VISIBLE);
                } else {
                    // Deshabilitar y finalizar la actividad de forma moderna
                    setEnabled(false);
                    finish();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        compassViewModel.startSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        compassViewModel.stopSensors();
    }

    private boolean checkPermissions() {
        boolean location = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        return location && camera;
    }

    private void requestRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        requestPermissionLauncher.launch(permissions.toArray(new String[0]));
    }

    private void checkGpsAndTakePick() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager != null && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

        if (isGpsEnabled) {
            dispatchTakePictureIntent();
        } else {
            showGpsDisabledDialog();
        }
    }

    private void showGpsDisabledDialog() {
        new AlertDialog.Builder(this)
                .setTitle("GPS Desactivado")
                .setMessage(R.string.gps_desactivado)
                .setPositiveButton("Configuración", (dialog, which) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            photoFile = createImageFile();
            Uri photoUri = FileProvider.getUriForFile(this,
                    getApplicationContext().getPackageName() + ".provider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            takePictureLauncher.launch(takePictureIntent);
        } catch (IOException ex) {
            Toast.makeText(this, R.string.error_crear_archivo, Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("PHOTO_" + timeStamp, ".jpg", storageDir);
    }

    private void processCapturedPhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        addWatermark(location);
                    } else {
                        fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) addWatermark(lastLoc);
                            else {
                                Location dummyLocation = new Location("dummy");
                                dummyLocation.setLatitude(0);
                                dummyLocation.setLongitude(0);
                                addWatermark(dummyLocation);
                            }
                        });
                    }
                });
    }

    private void addWatermark(Location location) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            Bitmap original = BitmapFactory.decodeFile(photoFile.getAbsolutePath(), options);
            if (original == null) return;

            Bitmap rotatedBitmap = rotateImageIfRequired(original, photoFile.getAbsolutePath());

            Bitmap bitmap = rotatedBitmap.isMutable() ? rotatedBitmap : rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (rotatedBitmap != bitmap) rotatedBitmap.recycle();

            Canvas canvas = new Canvas(bitmap);

            String text = String.format(Locale.getDefault(), "Lat: %.6f | Lon: %.6f | Dir: %.0f°",
                    location.getLatitude(), location.getLongitude(), currentAzimuth);

            Paint paint = new Paint();
            paint.setAntiAlias(true);
            float textSize = bitmap.getWidth() / 22f;
            paint.setTextSize(textSize);

            Rect bounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);

            float x = bitmap.getWidth() * 0.05f;
            float y = bitmap.getHeight() - (bitmap.getHeight() * 0.05f);

            Paint bgPaint = new Paint();
            bgPaint.setColor(Color.BLACK);
            bgPaint.setAlpha(160);
            canvas.drawRect(x - 25, y - bounds.height() - 25, x + bounds.width() + 25, y + 25, bgPaint);

            paint.setColor(Color.WHITE);
            canvas.drawText(text, x, y, paint);

            markedBitmap = bitmap;
            binding.imgPreview.setImageBitmap(markedBitmap);
            binding.btnHacerFoto.setVisibility(View.GONE);
            binding.btnAbout.setVisibility(View.GONE);
            binding.layoutPreview.setVisibility(View.VISIBLE);

            if (photoFile.exists()) {
                if (!photoFile.delete()) {
                    android.util.Log.e("MainActivity", "No se pudo borrar el archivo temporal");
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error en marca: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap rotateImageIfRequired(Bitmap img, String path) throws IOException {
        ExifInterface ei = new ExifInterface(path);
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotateImage(img, 90);
            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotateImage(img, 180);
            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotateImage(img, 270);
            default:
                return img;
        }
    }

    private static Bitmap rotateImage(Bitmap img, int degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        Bitmap rotatedImg = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
        img.recycle();
        return rotatedImg;
    }

    private void saveImageToGallery(Bitmap bitmap) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "GEO_PHOTO_" + timeStamp + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GPS_Photos");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        try {
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                        out.flush();
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                } else {
                    String path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "GPS_Photos/" + fileName).getAbsolutePath();
                    MediaScannerConnection.scanFile(this, new String[]{path}, null, null);
                }
                Toast.makeText(this, "¡Foto guardada en Galería/GPS_Photos!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
