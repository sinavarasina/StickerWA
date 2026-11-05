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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import id.my.sinanonym.stickerwa.data.StickerRepository;
import id.my.sinanonym.stickerwa.data.model.StickerPack;
import id.my.sinanonym.stickerwa.ui.base.BaseActivity;
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

        stickerPackList = getIntent().getParcelableArrayListExtra(EXTRA_STICKER_PACK_LIST_DATA);
        if (stickerPackList == null) {
            stickerPackList = StickerRepository.fetchStickerPacks(this);
        }
        showStickerPackList(stickerPackList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Context appContext = getApplicationContext();
        final List<StickerPack> packsToCheck = new ArrayList<>(stickerPackList);
        whitelistTaskFuture = AppExecutors.execute(
                () -> {
                    for (StickerPack stickerPack : packsToCheck) {
                        stickerPack.setIsWhitelisted(WhitelistCheck.isWhitelisted(appContext, stickerPack.identifier));
                    }
                    return packsToCheck;
                },
                result -> {
                    allStickerPacksListAdapter.setStickerPackList(result);
                    allStickerPacksListAdapter.notifyDataSetChanged();
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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showStickerPackList(List<StickerPack> stickerPackList) {
        allStickerPacksListAdapter = new StickerPackListAdapter(
                stickerPackList,
                pack -> WhatsAppStickerLauncher.addStickerPackToWhatsApp(this, pack.identifier, pack.name),
                this::openFloatingPackCard
        );
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
