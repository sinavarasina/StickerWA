package id.my.sinanonym.stickerwa.ui.create;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.core.AppExecutors;
import id.my.sinanonym.stickerwa.core.StickerImageConverter;
import id.my.sinanonym.stickerwa.data.UserStickerPackManager;
import id.my.sinanonym.stickerwa.data.model.Sticker;
import id.my.sinanonym.stickerwa.data.model.StickerPack;
import id.my.sinanonym.stickerwa.provider.StickerContentProvider;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;

public class CreateStickerPackActivity extends BaseActivity {

    private static final int REQ_PICK_TRAY = 101;
    private static final int REQ_PICK_STICKERS = 102;
    private static final int MAX_STICKERS = 30;
    private static final int MIN_STICKERS = 1;

    private EditText inputName;
    private EditText inputPublisher;
    private EditText inputEmail;
    private EditText inputWebsite;

    private ImageView trayPreview;
    private TextView traySubtitle;
    private TextView stickersCounter;
    private View progressOverlay;

    private Uri trayUri;
    private final List<Uri> stickerUris = new ArrayList<>();
    private CreateStickerItemAdapter stickerAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_pack);

        View appBar = findViewById(R.id.app_bar);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(R.string.title_create_sticker_pack);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        if (appBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, insets) -> {
                Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int leftPadding = Math.max(cutoutInsets.left, sysBars.left);
                int rightPadding = Math.max(cutoutInsets.right, sysBars.right);
                v.setPadding(leftPadding, sysBars.top, rightPadding, 0);
                return insets;
            });
        }

        View scrollView = findViewById(R.id.create_scroll_view);
        if (scrollView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
                Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
                int leftPadding = Math.max(cutoutInsets.left, sysBars.left);
                int rightPadding = Math.max(cutoutInsets.right, sysBars.right);
                float density = getResources().getDisplayMetrics().density;
                int sidePad = (int) (16 * density);
                int topPad = (int) (12 * density);
                int botInset = Math.max(sysBars.bottom, imeInsets.bottom);
                int botPad = (int) (32 * density) + botInset;
                v.setPadding(leftPadding + sidePad, topPad, rightPadding + sidePad, botPad);
                return insets;
            });
        }

        inputName = findViewById(R.id.input_pack_name);
        inputPublisher = findViewById(R.id.input_pack_publisher);
        inputEmail = findViewById(R.id.input_pack_email);
        inputWebsite = findViewById(R.id.input_pack_website);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (inputName != null) inputName.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            if (inputPublisher != null) inputPublisher.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            if (inputEmail != null) inputEmail.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            if (inputWebsite != null) inputWebsite.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        trayPreview = findViewById(R.id.tray_preview);
        traySubtitle = findViewById(R.id.tray_subtitle);
        stickersCounter = findViewById(R.id.stickers_counter);
        progressOverlay = findViewById(R.id.progress_overlay);

        View cardPickTray = findViewById(R.id.card_pick_tray);
        if (cardPickTray != null) {
            cardPickTray.setOnClickListener(v -> pickTrayImage());
        }

        View btnAddStickers = findViewById(R.id.btn_add_stickers);
        if (btnAddStickers != null) {
            btnAddStickers.setOnClickListener(v -> pickStickerImages());
        }

        View btnSavePack = findViewById(R.id.btn_save_pack);
        if (btnSavePack != null) {
            btnSavePack.setOnClickListener(v -> saveStickerPack());
        }

        RecyclerView recyclerStickers = findViewById(R.id.recycler_stickers);
        stickerAdapter = new CreateStickerItemAdapter(stickerUris, position -> {
            if (position >= 0 && position < stickerUris.size()) {
                stickerUris.remove(position);
                stickerAdapter.notifyItemRemoved(position);
                updateCounter();
            }
        });
        if (recyclerStickers != null) {
            recyclerStickers.setAdapter(stickerAdapter);
        }

        updateCounter();
    }

    private void updateCounter() {
        if (stickersCounter != null) {
            stickersCounter.setText(getString(R.string.create_pack_stickers_count_format, stickerUris.size()));
        }
    }

    private void pickTrayImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.create_pack_tray_title)), REQ_PICK_TRAY);
    }

    private void pickStickerImages() {
        if (stickerUris.size() >= MAX_STICKERS) {
            Toast.makeText(this, R.string.create_pack_error_max_stickers, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.create_pack_add_stickers)), REQ_PICK_STICKERS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        if (requestCode == REQ_PICK_TRAY) {
            Uri uri = data.getData();
            if (uri != null) {
                trayUri = uri;
                trayPreview.setImageURI(uri);
                trayPreview.setImageTintList(null);
                traySubtitle.setText("Icon selected");
            }
        } else if (requestCode == REQ_PICK_STICKERS) {
            ClipData clipData = data.getClipData();
            if (clipData != null) {
                int count = clipData.getItemCount();
                for (int i = 0; i < count; i++) {
                    if (stickerUris.size() >= MAX_STICKERS) {
                        Toast.makeText(this, R.string.create_pack_error_max_stickers, Toast.LENGTH_SHORT).show();
                        break;
                    }
                    Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null && !stickerUris.contains(uri)) {
                        stickerUris.add(uri);
                    }
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                if (!stickerUris.contains(uri)) {
                    if (stickerUris.size() < MAX_STICKERS) {
                        stickerUris.add(uri);
                    } else {
                        Toast.makeText(this, R.string.create_pack_error_max_stickers, Toast.LENGTH_SHORT).show();
                    }
                }
            }
            stickerAdapter.notifyDataSetChanged();
            updateCounter();
        }
    }

    private void saveStickerPack() {
        String name = inputName.getText() != null ? inputName.getText().toString().trim() : "";
        String publisher = inputPublisher.getText() != null ? inputPublisher.getText().toString().trim() : "";
        String email = inputEmail.getText() != null ? inputEmail.getText().toString().trim() : "";
        String website = inputWebsite.getText() != null ? inputWebsite.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.create_pack_error_name, Toast.LENGTH_SHORT).show();
            inputName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(publisher)) {
            Toast.makeText(this, R.string.create_pack_error_publisher, Toast.LENGTH_SHORT).show();
            inputPublisher.requestFocus();
            return;
        }
        if (trayUri == null) {
            Toast.makeText(this, R.string.create_pack_error_tray, Toast.LENGTH_SHORT).show();
            return;
        }
        if (stickerUris.size() < MIN_STICKERS) {
            Toast.makeText(this, R.string.create_pack_error_min_stickers, Toast.LENGTH_SHORT).show();
            return;
        }
        if (stickerUris.size() > MAX_STICKERS) {
            Toast.makeText(this, R.string.create_pack_error_max_stickers, Toast.LENGTH_SHORT).show();
            return;
        }

        progressOverlay.setVisibility(View.VISIBLE);

        final List<Uri> urisToProcess = new ArrayList<>(stickerUris);
        final Uri selectedTrayUri = trayUri;

        AppExecutors.execute(
                () -> {
                    String identifier = "custom_" + System.currentTimeMillis();
                    File packDir = UserStickerPackManager.getCustomPackDir(getApplicationContext(), identifier);

                    // 1. Process and save tray icon
                    byte[] trayBytes = StickerImageConverter.convertToTrayWebp(getApplicationContext(), selectedTrayUri);
                    File trayFile = new File(packDir, "tray.webp");
                    try (FileOutputStream fos = new FileOutputStream(trayFile)) {
                        fos.write(trayBytes);
                    }

                    // 2. Process and save stickers
                    List<Sticker> stickers = new ArrayList<>();
                    for (int i = 0; i < urisToProcess.size(); i++) {
                        Uri stickerUri = urisToProcess.get(i);
                        byte[] stickerBytes = StickerImageConverter.convertToStickerWebp(getApplicationContext(), stickerUri);
                        String fileName = (i + 1) + ".webp";
                        File stickerFile = new File(packDir, fileName);
                        try (FileOutputStream fos = new FileOutputStream(stickerFile)) {
                            fos.write(stickerBytes);
                        }
                        Sticker sticker = new Sticker(fileName, Collections.singletonList("⭐"), "Sticker " + (i + 1));
                        sticker.setSize(stickerBytes.length);
                        stickers.add(sticker);
                    }

                    // 3. Construct pack and save
                    StickerPack pack = new StickerPack(
                            identifier,
                            name,
                            publisher,
                            "tray.webp",
                            email,
                            website,
                            "",
                            "",
                            "1",
                            false,
                            false
                    );
                    pack.setIsCustom(true);
                    pack.setStickers(stickers);

                    UserStickerPackManager.saveUserPack(getApplicationContext(), pack);
                    getContentResolver().notifyChange(StickerContentProvider.AUTHORITY_URI, null);

                    return pack;
                },
                createdPack -> {
                    progressOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.create_pack_success_toast, createdPack.name), Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                }
        );
    }
}
