/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package id.my.sinanonym.stickerwa.ui.base;

import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;

import id.my.sinanonym.stickerwa.core.ThemePreferences;

public abstract class BaseActivity extends AppCompatActivity {

    private String appliedPalette;
    private String appliedMode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appliedPalette = ThemePreferences.getPalette(this);
        appliedMode = ThemePreferences.getThemeMode(this);
        ThemePreferences.applyTheme(this);
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            getWindow().setAttributes(lp);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentPalette = ThemePreferences.getPalette(this);
        String currentMode = ThemePreferences.getThemeMode(this);
        if ((appliedPalette != null && !appliedPalette.equals(currentPalette))
                || (appliedMode != null && !appliedMode.equals(currentMode))) {
            recreate();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static final class MessageDialogFragment extends DialogFragment {
        private static final String ARG_TITLE_ID = "title_id";
        private static final String ARG_MESSAGE = "message";

        public static DialogFragment newInstance(@StringRes int titleId, String message) {
            DialogFragment fragment = new MessageDialogFragment();
            Bundle arguments = new Bundle();
            arguments.putInt(ARG_TITLE_ID, titleId);
            arguments.putString(ARG_MESSAGE, message);
            fragment.setArguments(arguments);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            @StringRes final int title = requireArguments().getInt(ARG_TITLE_ID);
            String message = requireArguments().getString(ARG_MESSAGE);

            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireActivity())
                    .setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> dismiss());

            if (title != 0) {
                dialogBuilder.setTitle(title);
            }
            return dialogBuilder.create();
        }
    }
}
