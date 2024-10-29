package gt.edu.umg.gallery_and_memories.galeria;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import gt.edu.umg.gallery_and_memories.R;
import gt.edu.umg.gallery_and_memories.database.DatabaseHelper;
import gt.edu.umg.gallery_and_memories.models.PhotoItem;

public class PhotoDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PHOTO_URI = "photo_uri";
    public static final String EXTRA_PHOTO_DESC = "photo_description";
    public static final String EXTRA_PHOTO_DATE = "photo_date";
    public static final String EXTRA_PHOTO_LAT = "photo_latitude";
    public static final String EXTRA_PHOTO_LON = "photo_longitude";
    public static final String EXTRA_PHOTO_ID = "photo_id";

    private DatabaseHelper dbHelper;
    private long photoId;
    private String photoUri;
    private EditText detailDescription;
    private Button btnEdit;
    private boolean isEditing = false;
    private LinearLayout editPhotoOptions;
    private Uri newPhotoUri;
    private double latitude, longitude;
    private String currentDate;

    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private ActivityResultLauncher<Intent> choosePhotoLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photo_detail);

        dbHelper = new DatabaseHelper(this);
        setupActivityLaunchers();

        photoId = getIntent().getLongExtra(EXTRA_PHOTO_ID, -1);
        photoUri = getIntent().getStringExtra(EXTRA_PHOTO_URI);
        latitude = getIntent().getDoubleExtra(EXTRA_PHOTO_LAT, 0);
        longitude = getIntent().getDoubleExtra(EXTRA_PHOTO_LON, 0);
        currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());

        initializeViews();
        loadPhotoData();
    }

    private void setupActivityLaunchers() {
        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        handleNewPhoto(newPhotoUri);
                    }
                });

        choosePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleNewPhoto(result.getData().getData());
                    }
                });
    }

    private void initializeViews() {
        ImageView fullImageView = findViewById(R.id.fullImageView);
        detailDescription = findViewById(R.id.detailDescription);
        editPhotoOptions = findViewById(R.id.editPhotoOptions);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnDelete = findViewById(R.id.btnDelete);
        btnEdit = findViewById(R.id.btnEdit);
        Button btnTakeNewPhoto = findViewById(R.id.btnTakeNewPhoto);
        Button btnChoosePhoto = findViewById(R.id.btnChoosePhoto);

        btnBack.setOnClickListener(v -> finish());
        btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        btnEdit.setOnClickListener(v -> toggleEditMode());
        btnTakeNewPhoto.setOnClickListener(v -> takeNewPhoto());
        btnChoosePhoto.setOnClickListener(v -> choosePhotoFromGallery());
    }

    private void toggleEditMode() {
        isEditing = !isEditing;
        if (isEditing) {
            // Entrar en modo edición
            detailDescription.setEnabled(true);
            detailDescription.setBackground(getResources().getDrawable(R.drawable.troleohelmado));
            detailDescription.requestFocus();
            editPhotoOptions.setVisibility(View.VISIBLE);
            btnEdit.setText("💾 Guardar");
        } else {
            // Guardar cambios
            saveChanges();
            detailDescription.setEnabled(false);
            detailDescription.setBackground(null);
            editPhotoOptions.setVisibility(View.GONE);
            btnEdit.setText("✏️ Editar");
        }
    }

    private void takeNewPhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = createImageFile();
        if (photoFile != null) {
            newPhotoUri = FileProvider.getUriForFile(this,
                    "gt.edu.umg.gallery_and_memories.fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, newPhotoUri);
            takePhotoLauncher.launch(takePictureIntent);
        }
    }

    private void choosePhotoFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        choosePhotoLauncher.launch(intent);
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir("Gallery");
        try {
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (Exception e) {
            Log.e("PhotoDetailActivity", "Error creating image file: " + e.getMessage());
            Toast.makeText(this, "Error al crear archivo de imagen", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void handleNewPhoto(Uri uri) {
        if (uri != null) {
            try {
                // Actualizar la vista previa
                ImageView fullImageView = findViewById(R.id.fullImageView);
                fullImageView.setImageURI(uri);

                // Guardar la nueva URI para cuando se guarden los cambios
                photoUri = uri.toString();

                // Actualizar la fecha
                currentDate = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
                TextView detailDateTime = findViewById(R.id.detailDateTime);
                detailDateTime.setText("Fecha: " + currentDate);
            } catch (Exception e) {
                Log.e("PhotoDetailActivity", "Error loading new photo: " + e.getMessage());
                Toast.makeText(this, "Error al cargar la nueva foto", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveChanges() {
        String newDescription = detailDescription.getText().toString().trim();
        PhotoItem photo = new PhotoItem(
                photoId,
                photoUri,
                newDescription,
                currentDate,
                latitude,
                longitude
        );

        boolean updated = dbHelper.updatePhoto(photo);
        if (updated) {
            Toast.makeText(this, "Cambios guardados exitosamente", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
        } else {
            Toast.makeText(this, "Error al guardar los cambios", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPhotoData() {
        String description = getIntent().getStringExtra(EXTRA_PHOTO_DESC);
        String date = getIntent().getStringExtra(EXTRA_PHOTO_DATE);

        // Mostrar la imagen
        ImageView fullImageView = findViewById(R.id.fullImageView);
        if (photoUri != null) {
            try {
                fullImageView.setImageURI(Uri.parse(photoUri));
            } catch (Exception e) {
                Log.e("PhotoDetailActivity", "Error loading image: " + e.getMessage());
                fullImageView.setImageResource(R.drawable.troleohelmado);
                Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show();
            }
        }

        // Mostrar los detalles
        detailDescription.setText(description);
        TextView detailDateTime = findViewById(R.id.detailDateTime);
        TextView detailLocation = findViewById(R.id.detailLocation);

        detailDateTime.setText("Fecha: " + date);
        detailLocation.setText(String.format(Locale.getDefault(),
                "Ubicación:\nLatitud: %.6f\nLongitud: %.6f",
                latitude, longitude));
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar foto")
                .setMessage("¿Estás seguro que deseas eliminar esta foto?")
                .setPositiveButton("Eliminar", (dialog, which) -> deletePhoto())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deletePhoto() {
        if (photoId != -1) {
            try {
                boolean deleted = dbHelper.deletePhoto(photoId);
                if (deleted) {
                    // Intentar eliminar el archivo físico
                    try {
                        if (photoUri != null) {
                            Uri uri = Uri.parse(photoUri);
                            getContentResolver().delete(uri, null, null);
                        }
                    } catch (Exception e) {
                        Log.e("PhotoDetailActivity", "Error al eliminar archivo físico: " + e.getMessage());
                    }

                    Toast.makeText(this, "Foto eliminada exitosamente", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(this, "Error al eliminar la foto", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e("PhotoDetailActivity", "Error deleting photo: " + e.getMessage());
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    @Override
    public void onBackPressed() {
        if (isEditing) {
            new AlertDialog.Builder(this)
                    .setTitle("Cambios sin guardar")
                    .setMessage("¿Deseas guardar los cambios antes de salir?")
                    .setPositiveButton("Guardar", (dialog, which) -> {
                        saveChanges();
                        finish();
                    })
                    .setNegativeButton("Descartar", (dialog, which) -> finish())
                    .setNeutralButton("Cancelar", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }
}