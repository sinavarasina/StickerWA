/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package id.my.sinanonym.stickerwa.ui.list;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import id.my.sinanonym.stickerwa.R;
import id.my.sinanonym.stickerwa.core.AppExecutors;
import id.my.sinanonym.stickerwa.core.StickerPackExporter;
import id.my.sinanonym.stickerwa.data.StickerRepository;
import id.my.sinanonym.stickerwa.data.UserStickerPackManager;
import id.my.sinanonym.stickerwa.data.model.StickerPack;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;
import id.my.sinanonym.stickerwa.ui.create.CreateStickerPackActivity;
import id.my.sinanonym.stickerwa.ui.settings.SettingsActivity;
import id.my.sinanonym.stickerwa.whatsapp.WhatsAppStickerLauncher;
import id.my.sinanonym.stickerwa.whatsapp.WhitelistCheck;

public class StickerPackListActivity extends BaseActivity {
    public static final String EXTRA_STICKER_PACK_LIST_DATA = "sticker_pack_list";
    private static final String TAG_FLOATING_PACK_DIALOG = "floating_sticker_pack_dialog";

    private GridLayoutManager packLayoutManager;
    private RecyclerView packRecyclerView;
    private StickerPackListAdapter allStickerPacksListAdapter;
    private ArrayList<StickerPack> stickerPackList;
    @Nullable
    private Future<?> whitelistTaskFuture;

    private View normalToolbarView;
    private View selectionToolbarView;
    private TextView selectionTitle;
    private View fabCreatePack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_pack_list);
        packRecyclerView = findViewById(R.id.sticker_pack_list);

        View appBar = findViewById(R.id.app_bar);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }

        normalToolbarView = findViewById(R.id.normal_toolbar_view);
        selectionToolbarView = findViewById(R.id.selection_toolbar_view);
        selectionTitle = findViewById(R.id.selection_title);
        View selectionCloseButton = findViewById(R.id.selection_close_button);
        View selectionSubmitButton = findViewById(R.id.selection_submit_button);
        View selectionDeleteButton = findViewById(R.id.selection_delete_button);

        if (selectionCloseButton != null) {
            selectionCloseButton.setOnClickListener(v -> {
                if (allStickerPacksListAdapter != null) {
                    allStickerPacksListAdapter.exitSelectionMode();
                }
            });
        }

        if (selectionSubmitButton != null) {
            selectionSubmitButton.setOnClickListener(v -> confirmAndSubmitSelectedPacks());
        }

        if (selectionDeleteButton != null) {
            selectionDeleteButton.setOnClickListener(v -> confirmAndDeleteSelectedPacks());
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

        ViewCompat.setOnApplyWindowInsetsListener(packRecyclerView, (v, insets) -> {
            Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int leftPadding = Math.max(cutoutInsets.left, sysBars.left);
            int rightPadding = Math.max(cutoutInsets.right, sysBars.right);
            int bottomPadding = sysBars.bottom;
            float density = getResources().getDisplayMetrics().density;
            int sidePad = (int) (9 * density);
            int topPad = (int) (8 * density);
            int botPad = (int) (24 * density);
            v.setPadding(leftPadding + sidePad, topPad, rightPadding + sidePad, bottomPadding + botPad);
            return insets;
        });

        fabCreatePack = findViewById(R.id.fab_create_pack);
        if (fabCreatePack != null) {
            fabCreatePack.setOnClickListener(v -> startActivity(new Intent(this, CreateStickerPackActivity.class)));
            ViewCompat.setOnApplyWindowInsetsListener(fabCreatePack, (v, insets) -> {
                Insets cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
                Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                float density = getResources().getDisplayMetrics().density;
                int baseMarginEnd = (int) (20 * density);
                int baseMarginBottom = (int) (24 * density);
                int rightMargin = baseMarginEnd + Math.max(cutoutInsets.right, sysBars.right);
                int botMargin = baseMarginBottom + sysBars.bottom;
                if (v.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    lp.rightMargin = rightMargin;
                    lp.bottomMargin = botMargin;
                    v.setLayoutParams(lp);
                }
                return insets;
            });
        }

        stickerPackList = getIntent().getParcelableArrayListExtra(EXTRA_STICKER_PACK_LIST_DATA);
        if (stickerPackList == null) {
            stickerPackList = StickerRepository.fetchStickerPacks(this);
        }
        showStickerPackList(stickerPackList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPacks();
        StickerPackExporter.cleanSubmissionCache(this);
    }

    public void refreshPacks() {
        final Context appContext = getApplicationContext();
        whitelistTaskFuture = AppExecutors.execute(
                () -> {
                    ArrayList<StickerPack> packs = StickerRepository.fetchStickerPacks(appContext);
                    for (StickerPack stickerPack : packs) {
                        stickerPack.setIsWhitelisted(WhitelistCheck.isWhitelisted(appContext, stickerPack.identifier));
                    }
                    return packs;
                },
                result -> {
                    stickerPackList = result;
                    if (allStickerPacksListAdapter != null) {
                        allStickerPacksListAdapter.setStickerPackList(result);
                        allStickerPacksListAdapter.notifyDataSetChanged();
                    }
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        AppExecutors.cancel(whitelistTaskFuture);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        WhatsAppStickerLauncher.handleActivityResult(this, requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean inSelection = allStickerPacksListAdapter != null && allStickerPacksListAdapter.isSelectionMode();
        MenuItem settingsItem = menu.findItem(R.id.action_settings);
        if (settingsItem != null) {
            settingsItem.setVisible(!inSelection);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (allStickerPacksListAdapter != null && allStickerPacksListAdapter.isSelectionMode()) {
            allStickerPacksListAdapter.exitSelectionMode();
            return;
        }
        super.onBackPressed();
    }

    private void onSelectionChanged(int selectedCount) {
        if (selectedCount > 0) {
            if (normalToolbarView != null) normalToolbarView.setVisibility(View.GONE);
            if (selectionToolbarView != null) selectionToolbarView.setVisibility(View.VISIBLE);
            if (selectionTitle != null) {
                selectionTitle.setText(getString(R.string.selection_count_format, selectedCount));
            }
            if (fabCreatePack != null) fabCreatePack.setVisibility(View.GONE);
        } else {
            if (normalToolbarView != null) normalToolbarView.setVisibility(View.VISIBLE);
            if (selectionToolbarView != null) selectionToolbarView.setVisibility(View.GONE);
            if (fabCreatePack != null) fabCreatePack.setVisibility(View.VISIBLE);
        }
        invalidateOptionsMenu();
    }

    private void confirmAndSubmitSelectedPacks() {
        if (allStickerPacksListAdapter == null) return;
        List<StickerPack> selected = allStickerPacksListAdapter.getSelectedPacks();
        if (selected.isEmpty()) return;

        int count = selected.size();
        String message = (count == 1)
                ? getString(R.string.submit_to_author_confirm_msg)
                : getString(R.string.batch_submit_confirm, count);

        new AlertDialog.Builder(this)
                .setTitle(R.string.submit_to_author_title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_btn_submit, (dialog, which) -> {
                    StickerPackExporter.launchBatchSubmissionEmail(this, selected);
                    allStickerPacksListAdapter.exitSelectionMode();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmAndDeleteSelectedPacks() {
        if (allStickerPacksListAdapter == null) return;
        List<StickerPack> selected = allStickerPacksListAdapter.getSelectedPacks();
        if (selected.isEmpty()) return;

        int count = selected.size();
        String title = (count == 1)
                ? getString(R.string.delete_pack_button)
                : getString(R.string.batch_delete_title, count);
        String message = (count == 1)
                ? getString(R.string.delete_pack_confirm)
                : getString(R.string.batch_delete_confirm, count);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_btn_delete, (dialog, which) -> {
                    for (StickerPack pack : selected) {
                        UserStickerPackManager.deleteUserPack(this, pack.identifier);
                    }
                    allStickerPacksListAdapter.exitSelectionMode();
                    refreshPacks();
                    Toast.makeText(this, getString(R.string.batch_delete_success, count), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showStickerPackList(List<StickerPack> stickerPackList) {
        allStickerPacksListAdapter = new StickerPackListAdapter(
                stickerPackList,
                pack -> WhatsAppStickerLauncher.addStickerPackToWhatsApp(this, pack.identifier, pack.name),
                this::openFloatingPackCard
        );
        allStickerPacksListAdapter.setOnSelectionChangedListener(this::onSelectionChanged);
        packRecyclerView.setAdapter(allStickerPacksListAdapter);
        packLayoutManager = new GridLayoutManager(this, calculateSpanCount());
        packRecyclerView.setLayoutManager(packLayoutManager);
    }

    private void openFloatingPackCard(@NonNull StickerPack pack) {
        StickerPackFloatingDialogFragment dialog = StickerPackFloatingDialogFragment.newInstance(pack);
        dialog.show(getSupportFragmentManager(), TAG_FLOATING_PACK_DIALOG);
    }

    private int calculateSpanCount() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float screenWidthDp = dm.widthPixels / dm.density;
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isLandscape) {
            return Math.max(3, (int) (screenWidthDp / 190f));
        }
        return Math.max(2, (int) (screenWidthDp / 180f));
    }
}
