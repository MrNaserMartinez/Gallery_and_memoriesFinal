package gt.edu.umg.gallery_and_memories.galeria;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import gt.edu.umg.gallery_and_memories.MainActivity;
import gt.edu.umg.gallery_and_memories.R;
import gt.edu.umg.gallery_and_memories.database.DatabaseHelper;
import gt.edu.umg.gallery_and_memories.models.PhotoItem;
import gt.edu.umg.gallery_and_memories.adapters.PhotoAdapter;

public class GaleriaActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private ImageView imagePreview;
    private TextView photoInfoText;
    private EditText photoDescription;
    private RecyclerView photoRecyclerView;
    private Uri photoUri;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private Button btnRegresar;
    private DatabaseHelper dbHelper;
    private PhotoAdapter photoAdapter;
    private Location currentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.galeria_activity);

        dbHelper = new DatabaseHelper(this);

        initializeViews();
        setupLocationClient();
        setupCameraLauncher();
        setupPermissionLauncher();
        setupPhotoRecyclerView();
        setupButtonListeners();
        loadPhotos();
    }

    private void setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                        if (!entry.getValue()) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        takePhoto();
                    } else {
                        Toast.makeText(this, "Se requieren todos los permisos para usar la cámara y la ubicación", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void initializeViews() {
        Button captureButton = findViewById(R.id.captureButton);
        imagePreview = findViewById(R.id.imagePreview);
        photoInfoText = findViewById(R.id.photoInfoText);
        photoDescription = findViewById(R.id.photoDescription);
        photoRecyclerView = findViewById(R.id.photoRecyclerView);
        btnRegresar = findViewById(R.id.btnRegresar);

        captureButton.setOnClickListener(v -> checkPermissionsAndTakePhoto());
    }

    private void setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void setupCameraLauncher() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleCameraResult();
                    } else {
                        Toast.makeText(this, "Captura de foto cancelada", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupPhotoRecyclerView() {
        photoRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        photoAdapter = new PhotoAdapter(new ArrayList<>(), this);
        photoRecyclerView.setAdapter(photoAdapter);
    }

    private void setupButtonListeners() {
        btnRegresar.setOnClickListener(v -> {
            finish();
        });
    }

    private void loadPhotos() {
        List<PhotoItem> photos = dbHelper.getAllPhotos();
        photoAdapter.updatePhotos(photos);
    }

    private boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissionsAndTakePhoto() {
        String[] requiredPermissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        boolean allPermissionsGranted = true;
        for (String permission : requiredPermissions) {
            if (!checkPermission(permission)) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            takePhoto();
        } else {
            permissionLauncher.launch(requiredPermissions);
        }
    }

    private void takePhoto() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "Nueva Foto " + new Date().getTime());
            values.put(MediaStore.Images.Media.DESCRIPTION, "Foto tomada desde la aplicación");
            photoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (photoUri != null) {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                cameraLauncher.launch(intent);
            } else {
                Toast.makeText(this, "Error al crear el archivo de la foto", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al iniciar la cámara: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleCameraResult() {
        try {
            imagePreview.setImageURI(null);
            imagePreview.setImageURI(photoUri);

            if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                fusedLocationClient.getLastLocation()
                        .addOnSuccessListener(this, location -> {
                            currentLocation = location;
                            updatePhotoInfo(location);
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Error al obtener la ubicación: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show());
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error al procesar la foto: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updatePhotoInfo(Location location) {
        if (location != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            String currentDate = sdf.format(new Date());

            String infoText = String.format(Locale.getDefault(),
                    "Fecha: %s\nUbicación:\nLatitud: %.6f\nLongitud: %.6f",
                    currentDate,
                    location.getLatitude(),
                    location.getLongitude()
            );

            photoInfoText.setText(infoText);

            String description = photoDescription.getText().toString().trim();
            if (description.isEmpty()) {
                description = "Sin descripción";
            }

            long photoId = dbHelper.insertPhoto(
                    photoUri.toString(),
                    description,
                    currentDate,
                    location.getLatitude(),
                    location.getLongitude()
            );

            if (photoId != -1) {
                Toast.makeText(this, "Foto guardada exitosamente", Toast.LENGTH_SHORT).show();
                photoDescription.setText("");
                loadPhotos();

                // Regresar al MainActivity después de guardar la foto
                finish();
            } else {
                Toast.makeText(this, "Error al guardar la foto", Toast.LENGTH_SHORT).show();
            }
        } else {
            photoInfoText.setText("No se pudo obtener la ubicación actual");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPhotos();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // La foto fue eliminada, actualizar la lista
            loadPhotos();
        }
    }
}